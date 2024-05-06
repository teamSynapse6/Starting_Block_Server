package com.startingblock.domain.crawling.oncampus.constant;

public final class SeoulConstant {

    private SeoulConstant() {}

    public final static String URL = "https://startup.snu.ac.kr/front/lounge/notice";
    public final static String TITLE1 = "//*[@id=\"content\"]/div/div[2]/div/ul/li[";
    public final static String TITLE2 = "]/a/strong[2]";
    public final static String DATE1 = "//*[@id=\"content\"]/div/div[2]/div/ul/li[";
    public final static String DATE2 = "]/a/span[2]";
    public final static String NOTIFICATION1 = "//*[@id=\"content\"]/div/div[2]/div/ul/li[";
    public final static String NOTIFICATION2 = "]/a/strong[1]";
    public final static String VISIBLE = "//*[@id=\"content\"]/div/div/div[1]/div[1]/div/div[1]/strong";
}
