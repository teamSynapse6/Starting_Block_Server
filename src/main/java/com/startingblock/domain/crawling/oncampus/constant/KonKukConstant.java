package com.startingblock.domain.crawling.oncampus.constant;

public final class KonKukConstant {

    private KonKukConstant() {}

    public final static String URL = "https://startup.konkuk.ac.kr/BoardList.do?menuSeq=43842&configSeq=51096";
    public final static String TITLE1 = "//*[@id=\"container\"]/section/div[2]/div[2]/div/div[2]/table/tbody/tr[";
    public final static String TITLE2 = "]/td[2]/div/div/dl/dt/a";
    public final static String DATE1 = "//*[@id=\"container\"]/section/div[2]/div[2]/div/div[2]/table/tbody/tr[";
    public final static String DATE2 = "]/td[2]/div/div/div/span[2]";
    public final static String VISIBLE = "//*[@id=\"container\"]/section/div[2]/div[2]/div/div[1]/div[1]/div[1]/div/dl/dt";
}
