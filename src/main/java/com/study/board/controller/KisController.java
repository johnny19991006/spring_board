package com.study.board.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

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

    private final WebClient webClient;
    private String path;
    private String tr_id;
    private final CompanyRepository companyRepository;

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
        LocalDateTime now = LocalDateTime.now();
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
    public Mono<String> CurrentPrice(@RequestParam("stockName") String stockName, Model model) {
        return companyRepository.findByName(stockName)
            .map(company -> {
                String url = KisConfig.REST_BASE_URL + "/uapi/domestic-stock/v1/quotations/inquire-price?fid_cond_mrkt_div_code=J&fid_input_iscd=" + company.getCode();
                return webClient.get()
                    .uri(url)
                    .header("content-type", "application/json")
                    .header("authorization", "Bearer " + accessTokenManager.getAccessToken())
                    .header("appkey", KisConfig.APPKEY)
                    .header("appsecret", KisConfig.APPSECRET)
                    .header("tr_id", "FHKST01010100")
                    .retrieve()
                    .bodyToMono(Body.class)
                    .doOnSuccess(body -> {
                        model.addAttribute("equity", body.getOutput());
                        model.addAttribute("stockName", stockName);
                        model.addAttribute("jobDate", getJobDateTime());
                    })
                    .doOnError(result -> System.out.println("*** error: " + result))
                    .thenReturn("equities");
            })
            .orElseGet(() -> {
                model.addAttribute("error", "No company found with the name: " + stockName);
                return Mono.just("equities");
            });
    }

}

