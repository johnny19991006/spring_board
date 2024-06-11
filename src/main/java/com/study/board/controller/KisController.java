package com.study.board.controller;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import com.study.board.dto.SentimentResponse;
import com.study.board.model.Body;
import com.study.board.model.IndexData;
import com.study.board.repository.CompanyRepository;
import com.study.board.util.AccessTokenManager;
import com.study.board.util.KisConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.function.client.WebClient;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;


@Controller
public class KisController {
    @Autowired
    private AccessTokenManager accessTokenManager;

    @Autowired
    private ReactiveRedisTemplate<String, Object> reactiveRedisTemplate;

    private final WebClient webClient;
    private String path;
    private String tr_id;
    private final CompanyRepository companyRepository;
    private ReactiveValueOperations<String, Object> reactiveValueOps;

    @PostConstruct
    public void init() {
        reactiveValueOps = reactiveRedisTemplate.opsForValue();
    }

    public KisController(WebClient.Builder webClientBuilder, CompanyRepository companyRepository) {
        this.webClient = webClientBuilder.baseUrl(KisConfig.REST_BASE_URL).build();
        this.companyRepository = companyRepository;
    }

    @GetMapping("/")
    public String index(Model model) {
        return "index";
    }

    @GetMapping("/indices")
    public String majorIndices(Model model) {

        List<Tuple2<String, String>> iscdsAndOtherVariable1 = Arrays.asList(
                Tuples.of("0001", "U"),
                Tuples.of("2001", "U"),
                Tuples.of("1001", "U")
        );

        Flux<IndexData> indicesFlux = Flux.fromIterable(iscdsAndOtherVariable1)
                .concatMap(tuple -> getMajorIndex(tuple.getT1(), tuple.getT2()))
                .map(jsonData -> {
                    ObjectMapper objectMapper = new ObjectMapper();
                    try {
                        return objectMapper.readValue(jsonData, IndexData.class);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                });

        List<IndexData> indicesList = indicesFlux.collectList().block();
        model.addAttribute("indicesKor", indicesList);

        model.addAttribute("jobDate", getJobDateTime());

        return "indices";
    }

    public String getStringToday() {
        LocalDate localDate = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        return localDate.format(formatter);
    }

    public String getJobDateTime() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return now.format(formatter);
    }

    public Mono<String> getMajorIndex(String iscd, String fid_cond_mrkt_div_code) {

        if (fid_cond_mrkt_div_code.equals("U")) {
            path = KisConfig.FHKUP03500100_PATH;
            tr_id = "FHKUP03500100";
        } else {
            path = KisConfig.FHKST03030100_PATH;
            tr_id = "FHKST03030100";
        }

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(path)
                        .queryParam("fid_cond_mrkt_div_code", fid_cond_mrkt_div_code)
                        .queryParam("fid_input_iscd", iscd)
                        .queryParam("fid_input_date_1", getStringToday())
                        .queryParam("fid_input_date_2", getStringToday())
                        .queryParam("fid_period_div_code", "D")
                        .build())
                .header("content-type","application/json")
                .header("authorization","Bearer " + accessTokenManager.getAccessToken())
                .header("appkey",KisConfig.APPKEY)
                .header("appsecret",KisConfig.APPSECRET)
                .header("tr_id",tr_id)
                .retrieve()
                .bodyToMono(String.class);

    }



    @GetMapping("/equities")
    public Mono<String> getCurrentPrice(@RequestParam("stockName") String stockName, Model model) {
        return reactiveValueOps.get(stockName)
            .flatMap(cachedData -> {
                if (cachedData instanceof Map) {
                    Map<String, Object> data = (Map<String, Object>) cachedData;
                    model.addAttribute("stockName", data.get("stockName"));
                    model.addAttribute("jobDate", data.get("jobDate"));
                    model.addAttribute("sentimentTotalSentiment", data.get("sentimentTotalSentiment"));
                    model.addAttribute("sentimentTotalScore", data.get("sentimentTotalScore"));
                    model.addAttribute("sentimentNegative", data.get("sentimentNegative"));
                    model.addAttribute("sentimentNeutral", data.get("sentimentNeutral"));
                    model.addAttribute("sentimentPositive", data.get("sentimentPositive"));
                    model.addAttribute("keywords", data.get("keywords") != null ? data.get("keywords") : new SentimentResponse.Keywords());

                    // Retrieve equity data separately without caching
                    return retrieveEquityData(stockName, model);
                } else {
                    model.addAttribute("error", "Unexpected data type in cache");
                    return Mono.just("equities");
                }
            })
            .switchIfEmpty(Mono.defer(() -> {
                return Mono.justOrEmpty(companyRepository.findByName(stockName))
                    .flatMap(company -> {
                        String kisUrl = "/uapi/domestic-stock/v1/quotations/inquire-price?fid_cond_mrkt_div_code=J&fid_input_iscd=" + company.getCode();
                        String sentimentUrl = "http://13.124.38.18:5000/" + company.getCode();

                        Mono<String> sentimentResponse = webClient.get()
                            .uri(sentimentUrl)
                            .retrieve()
                            .bodyToMono(String.class);

                        return sentimentResponse
                            .flatMap(sentimentBody -> {
                                ObjectMapper objectMapper = new ObjectMapper();
                                try {
                                    SentimentResponse sentimentResponseObj = objectMapper.readValue(sentimentBody, SentimentResponse.class);
                                    SentimentResponse.SentimentCount sentimentCount = sentimentResponseObj.getSentiment_count();
                                    SentimentResponse.Keywords keywords = sentimentResponseObj.getKeywords();

                                    // Cache data except for equity
                                    Map<String, Object> cacheData = new HashMap<>();
                                    cacheData.put("stockName", stockName);
                                    cacheData.put("jobDate", getJobDateTime());
                                    cacheData.put("sentimentTotalSentiment", sentimentResponseObj.getTotal_sentiment());
                                    cacheData.put("sentimentTotalScore", sentimentResponseObj.getTotal_score());
                                    cacheData.put("sentimentNegative", sentimentCount.getNegative());
                                    cacheData.put("sentimentNeutral", sentimentCount.getNeutral());
                                    cacheData.put("sentimentPositive", sentimentCount.getPositive());
                                    cacheData.put("keywords", keywords);

                                    reactiveValueOps.set(stockName, cacheData, Duration.ofHours(1)).subscribe(); // TTL 설정

                                    model.addAttribute("stockName", stockName);
                                    model.addAttribute("jobDate", getJobDateTime());
                                    model.addAttribute("sentimentTotalSentiment", sentimentResponseObj.getTotal_sentiment());
                                    model.addAttribute("sentimentTotalScore", sentimentResponseObj.getTotal_score());
                                    model.addAttribute("sentimentNegative", sentimentCount.getNegative());
                                    model.addAttribute("sentimentNeutral", sentimentCount.getNeutral());
                                    model.addAttribute("sentimentPositive", sentimentCount.getPositive());
                                    model.addAttribute("keywords", objectMapper.writeValueAsString(keywords));

                                    // Retrieve equity data separately without caching
                                    return retrieveEquityData(stockName, model);

                                } catch (Exception e) {
                                    model.addAttribute("error", "Error parsing sentiment data: " + e.getMessage());
                                    return Mono.just("equities");
                                }
                            })
                            .doOnError(result -> {
                                model.addAttribute("error", "Error occurred while processing request: " + result.getMessage());
                                System.out.println("*** error: " + result);
                            })
                            .defaultIfEmpty("equities");
                    })
                    .switchIfEmpty(Mono.defer(() -> {
                        model.addAttribute("error", "No company found with the name: " + stockName);
                        return Mono.just("equities");
                    }));
            }));
    }

    private Mono<String> retrieveEquityData(String stockName, Model model) {
        return Mono.justOrEmpty(companyRepository.findByName(stockName))
            .flatMap(company -> {
                String kisUrl = "/uapi/domestic-stock/v1/quotations/inquire-price?fid_cond_mrkt_div_code=J&fid_input_iscd=" + company.getCode();

                return webClient.get()
                    .uri(kisUrl)
                    .header("content-type", "application/json")
                    .header("authorization", "Bearer " + accessTokenManager.getAccessToken())
                    .header("appkey", KisConfig.APPKEY)
                    .header("appsecret", KisConfig.APPSECRET)
                    .header("tr_id", "FHKST01010100")
                    .retrieve()
                    .bodyToMono(Body.class)
                    .map(kisBody -> {
                        model.addAttribute("equity", kisBody.getOutput());
                        return "equities";
                    });
            })
            .switchIfEmpty(Mono.defer(() -> {
                model.addAttribute("error", "No company found with the name: " + stockName);
                return Mono.just("equities");
            }));
    }

}

