package com.startingblock.domain.crawling.oncampus.constant;

public final class HanYangConstant {

    private HanYangConstant() {}

    public final static String URL = "https://startup.hanyang.ac.kr/board/notice/list";
    public final static String TITLE1 = "//*[@id=\"wrap\"]/div[2]/section/div[1]/table/tbody/tr[";
    public final static String TITLE2 = "]/td[2]/a";
    public final static String DATE1 = "//*[@id=\"wrap\"]/div[2]/section/div[1]/table/tbody/tr[";
    public final static String DATE2 = "]/td[3]";
    public final static String VISIBLE = "//*[@id=\"wrap\"]/div[2]/section/div/h1";
}
