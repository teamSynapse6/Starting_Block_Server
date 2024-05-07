package com.startingblock.domain.crawling.oncampus;

import com.startingblock.domain.announcement.domain.Keyword;

import java.util.HashMap;
import java.util.Map;

public abstract class CampusClassify {

    public Keyword classifyAnnouncement(String title) {
        Map<String, Keyword> categoryMap = new HashMap<>();
        categoryMap.put("멘토", Keyword.MENTORING);
        categoryMap.put("멘토링", Keyword.MENTORING);
        categoryMap.put("상담", Keyword.MENTORING);
        categoryMap.put("자문", Keyword.MENTORING);

        categoryMap.put("동아리", Keyword.CLUB);
        categoryMap.put("클럽", Keyword.CLUB);
        categoryMap.put("club", Keyword.CLUB);

        categoryMap.put("특강", Keyword.LECTURE);
        categoryMap.put("강의", Keyword.LECTURE);
        categoryMap.put("콘서트", Keyword.LECTURE);
        categoryMap.put("강연", Keyword.LECTURE);
        categoryMap.put("토크쇼", Keyword.LECTURE);
        categoryMap.put("게릴라", Keyword.LECTURE);
        categoryMap.put("강좌", Keyword.LECTURE);
        categoryMap.put("교육", Keyword.LECTURE);

        categoryMap.put("경진대회", Keyword.CONTEST);
        categoryMap.put("경연대회", Keyword.CONTEST);
        categoryMap.put("아이디어톤", Keyword.CONTEST);
        categoryMap.put("아이디어 공모전", Keyword.CONTEST);
        categoryMap.put("아이디어공모전", Keyword.CONTEST);
        categoryMap.put("창업 공모전", Keyword.CONTEST);
        categoryMap.put("창업공모전", Keyword.CONTEST);
        categoryMap.put("창업 페스티벌", Keyword.CONTEST);
        categoryMap.put("창업페스티벌", Keyword.CONTEST);
        categoryMap.put("서바이벌", Keyword.CONTEST);

        categoryMap.put("캠프", Keyword.CAMP);
        categoryMap.put("해커톤", Keyword.CAMP);
        categoryMap.put("워크숍", Keyword.CAMP);
        categoryMap.put("데모데이", Keyword.CAMP);

        return getKeyword(title, categoryMap);
    }

    private static Keyword getKeyword(final String title, final Map<String, Keyword> categoryMap) {
        Map<Keyword, Integer> priorityMap = new HashMap<>();
        priorityMap.put(Keyword.CLUB, 1);
        priorityMap.put(Keyword.CAMP, 2);
        priorityMap.put(Keyword.CONTEST, 3);
        priorityMap.put(Keyword.LECTURE, 4);
        priorityMap.put(Keyword.MENTORING, 5);
        priorityMap.put(Keyword.ETC, 6);

        Keyword selectedCategory = Keyword.ETC;
        int minPriority = Integer.MAX_VALUE;

        for (Map.Entry<String, Keyword> entry : categoryMap.entrySet()) {
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
