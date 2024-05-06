package com.startingblock.domain.crawling.oncampus.constant;

public final class SungKyunKwanConstant {

    private SungKyunKwanConstant() {}

    public final static String URL = "https://www.skku.edu/skku/campus/skk_comm/notice01.do?mode=list&srCategoryId1=&srSearchKey=article_title&srSearchVal=%EC%B0%BD%EC%97%85";
    public final static String TITLE1 = "//*[@id=\"jwxe_main_content\"]/div/div/div[1]/div[1]/ul/li[";
    public final static String TITLE2 = "]/dl/dt/a";
    public final static String DATE1 = "//*[@id=\"jwxe_main_content\"]/div/div/div[1]/div[1]/ul/li[";
    public final static String DATE2 = "]/dl/dd/ul/li[3]";
    public final static String VISIBLE = "//*[@id=\"jwxe_main_content\"]/div/div/div/div/table[1]/thead/tr/th/em";
}
