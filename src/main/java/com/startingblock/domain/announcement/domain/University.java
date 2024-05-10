package com.startingblock.domain.announcement.domain;

public enum University {
    EWHA("이화여자대학교"),
    HAN_YANG("한양대학교"),
    HUFS("한국외국어대학교"),
    KON_KUK("건국대학교"),
    KOREA("고려대학교"),
    KYUNG_HEE("경희대학교"),
    SEOUL_TECH("서울과학기술대학교"),
    SEOUL("서울대학교"),
    SUNG_KYUN_KWAN("성균관대학교"),
    YON_SEI("연세대학교"),
    CATHOLIC("가톨릭대학교"),
    GANGSEO("강서대학교"),
    KYONGGI("경기대학교"),
    KWANGWOON("광운대학교"),
    KOOKMIN("국민대학교"),
    DUKSUNG("덕성여자대학교"),
    DONGGUK("동국대학교"),
    DONGDUK("동덕여자대학교"),
    MYONGJI("명지대학교"),
    SAHMYOOK("삼육대학교"),
    SANGMYUNG("상명대학교"),
    SOGANG("서강대학교"),
    SEOKYEONG("서경대학교"),
    SEOUL_EDUCATION("서울교육대학교"),
    UOS("서울시립대학교"),
    SEOUL_WOMAN("서울여자대학교"),
    HANYOUNG("한영대학교"),
    SUNGKONGHOE("성공회대학교"),
    SUNGSHIN("성신여자대학교"),
    SEJONG("세종대학교"),
    SOOKMYUNG("숙명여자대학교"),
    SOONGSIL("숭실대학교"),
    CHUNG_ANG("중앙대학교"),
    CHONGSHIN("총신대학교"),
    CHUGYE("추계예술대학교"),
    KNSU("한국교원대학교"),
    HANSUNG("한성대학교"),
    HONGIK("홍익대학교");

    private final String name;

    University(String name) {
        this.name = name;
    }

    public static University fromName(String name) {
        for (University uni : University.values()) {
            if (uni.name.equals(name)) {
                return uni;
            }
        }
        return null;
    }

}
