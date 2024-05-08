package com.startingblock.domain.crawling.oncampus.constant;

public final class EwhaConstant {

    private EwhaConstant() {}

    public final static String URL = "https://startup.ewha.ac.kr/board/notice";
    public final static String TITLE1 = "//*[@id=\"wrap\"]/div[2]/div[2]/div[2]/div/div/div/div[1]/table/tbody/tr[";
    public final static String TITLE2 = "]/td[3]/a";
    public final static String DATE1 = "//*[@id=\"wrap\"]/div[2]/div[2]/div[2]/div/div/div/div[1]/table/tbody/tr[";
    public final static String DATE2 = "]/td[4]";
    public final static String VISIBLE = "//*[@id=\"wrap\"]/div[2]/div[2]/div[2]/div/div/div/div[1]/h3";
}
