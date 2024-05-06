package com.startingblock.domain.crawling.oncampus;

import java.util.HashMap;
import java.util.Map;

public abstract class CampusClassify {

    public String classifyAnnouncement(String title) {
        Map<String, String> categoryMap = new HashMap<>();
        categoryMap.put("멘토", "창업 멘토링");
        categoryMap.put("멘토링", "창업 멘토링");
        categoryMap.put("상담", "창업 멘토링");
        categoryMap.put("자문", "창업 멘토링");

        categoryMap.put("동아리", "창업 동아리");
        categoryMap.put("클럽", "창업 동아리");
        categoryMap.put("club", "창업 동아리");

        categoryMap.put("특강", "창업 특강");
        categoryMap.put("강의", "창업 특강");
        categoryMap.put("콘서트", "창업 특강");
        categoryMap.put("강연", "창업 특강");
        categoryMap.put("토크쇼", "창업 특강");
        categoryMap.put("게릴라", "창업 특강");
        categoryMap.put("강좌", "창업 특강");
        categoryMap.put("교육", "창업 특강");

        categoryMap.put("경진대회", "창업 경진대회");
        categoryMap.put("경연대회", "창업 경진대회");
        categoryMap.put("아이디어톤", "창업 경진대회");
        categoryMap.put("아이디어 공모전", "창업 경진대회");
        categoryMap.put("아이디어공모전", "창업 경진대회");
        categoryMap.put("창업 공모전", "창업 경진대회");
        categoryMap.put("창업공모전", "창업 경진대회");
        categoryMap.put("창업 페스티벌", "창업 경진대회");
        categoryMap.put("창업페스티벌", "창업 경진대회");
        categoryMap.put("서바이벌", "창업 경진대회");

        categoryMap.put("캠프", "창업 캠프");
        categoryMap.put("해커톤", "창업 캠프");
        categoryMap.put("워크숍", "창업 캠프");
        categoryMap.put("데모데이", "창업 캠프");

        return getKeyword(title, categoryMap);
    }

    private static String getKeyword(final String title, final Map<String, String> categoryMap) {
        Map<String, Integer> priorityMap = new HashMap<>();
        priorityMap.put("창업 동아리", 1);
        priorityMap.put("창업 캠프", 2);
        priorityMap.put("창업 경진대회", 3);
        priorityMap.put("창업 특강", 4);
        priorityMap.put("창업 멘토링", 5);
        priorityMap.put("기타", 6);

        String selectedCategory = "기타";
        int minPriority = Integer.MAX_VALUE;

        for (Map.Entry<String, String> entry : categoryMap.entrySet()) {
            if (title.contains(entry.getKey())) {
                int currentPriority = priorityMap.get(entry.getValue());
                if (currentPriority < minPriority) {
                    minPriority = currentPriority;
                    selectedCategory = entry.getValue();
                }
            }
        }
        return selectedCategory;
    }
}
