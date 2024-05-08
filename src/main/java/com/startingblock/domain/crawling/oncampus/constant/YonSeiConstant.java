package com.startingblock.domain.crawling.oncampus.constant;

public final class YonSeiConstant {

    private YonSeiConstant() {}

    public final static String URL = "https://venture.yonsei.ac.kr/board/notice";
    public final static String TITLE1 = "//*[@id=\"wrap\"]/div[2]/section/div/div[3]/table/tbody/tr[";
    public final static String TITLE2 = "]/td[2]/a";
    public final static String DATE1 = "//*[@id=\"wrap\"]/div[2]/section/div/div[3]/table/tbody/tr[";
    public final static String DATE2 = "]/td[3]";
    public final static String VISIBLE = "//*[@id=\"title\"]/div[1]/h3";
}
