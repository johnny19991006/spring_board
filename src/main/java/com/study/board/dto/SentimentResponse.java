package com.study.board.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
public class SentimentResponse {
    private SentimentCount sentiment_count;
    private double total_score;
    private String total_sentiment;
    private Keywords keywords;

    @Getter
    public static class SentimentCount {
        private SentimentDetail negative;
        private SentimentDetail neutral;
        private SentimentDetail positive;
    }

    @Getter
    public static class SentimentDetail {
        private Integer comments;
        private Integer investing;
        private Integer news;
    }

    @Getter
    public static class Keywords {
        private List<String> negative;
        private List<String> neutral;
        private List<String> positive;
        private List<String> news;
        private List<String> total;
    }
}
