package com.startingblock.domain.gpt.constant;


public final class GptConstant {

    private GptConstant(){}
    public static final String AUTHORIZATION = "Authorization";
    public static final String BEARER = "Bearer ";
    public static final String CHAT_MODEL = "gpt-4o";
    public static final Integer MAX_TOKEN = 1000;
    public static final String USER = "user";
    public static final String SYSTEM = "system";
    public static final Double TEMPERATURE = 0.3;
    public static final String MEDIA_TYPE = "application/json; charset=UTF-8";
    public static final String CHAT_URL = "https://api.openai.com/v1/chat/completions";

    // 프롬프트
    public static final String CHECK_DUPLICATE_PROMPT =
            """
                    당신은 창업 지원사업에 대한 질문을 기존의 질문들과 비교해주는 사람입니다.\s
                    [기존 질문]은 JSON 형태로, "qid"는 질문의 ID값, "content"는 질문의 내용입니다.
                    newQuestion 을 oldQuestions 리스트의 "content"와 비교해서 유사한 질문이 있다면 유사한 질문의 "qid"를, 없다면 0을 숫자만 출력하세요.
                    """;

    public static final String CHECK_SIMILARITY_PROMPT =
            """
                    당신은 창업 지원사업에 대한 여러가지 질문들을 유사한 질문들끼리 묶어주는 사람입니다.
                    질문들은 JSON 형태로, "qid"는 질문의 ID값, "content"는 질문의 내용입니다.
                    질문 리스트의 "content" 끼리 비교해서 유사한 질문이 있다면 하나의 질문으로 묶어주세요.
                    유사한 질문의 내용을 정리해서 "content"에 담아주고, 이때 사용한 질문들의 "qid"를 "usedId" 배열에 담아서 JSON 형태로 출력하세요.
                                        
                    제가 의도하는 출력 형식은 다음과 같습니다. 반드시 아래 형식을 지키세요.
                    [
                    {"content":"제출일이 언제인가요?","usedId":[10001, 10002]},
                    {"content":"마감기한 이후에 제출 시 어떻게 하나요?","usedId":[10003]},
                    ]
                    """;
}
