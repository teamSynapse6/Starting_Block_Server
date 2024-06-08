package com.startingblock.domain.gpt.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.startingblock.domain.gpt.dto.DuplicateReq;
import com.startingblock.domain.gpt.dto.GptMessage;
import com.startingblock.domain.gpt.dto.GptRes;
import com.startingblock.domain.gpt.dto.GroupingQuestionReq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.startingblock.domain.gpt.constant.GptConstant.*;


@Service
@RequiredArgsConstructor
@Slf4j
public class GptService {

    private final RestTemplate restTemplate;

    @Value(value = "${gpt.api-key}")
    private String apiKey;

    public Long checkDuplicateQuestion(final DuplicateReq duplicateReq) throws JsonProcessingException {
        List<GptMessage> messages = new ArrayList<>();

        // gpt 역할(프롬프트) 설정
        messages.add(GptMessage.builder()
                .role(SYSTEM)
                .content(CHECK_DUPLICATE_PROMPT)
                .build());

        // 실제 요청
        messages.add(GptMessage.builder()
                .role(USER)
                .content(duplicateReq.toJson())
                .build());

        log.info("Request Messages: {}", messages);

        HashMap<String, Object> requestBody = createRequestBody(messages);

        GptRes chatGptRes = getResponse(createHttpEntity(requestBody));

        String response = chatGptRes.getChoices().get(0).getMessage().getContent();
        log.info("Response: {}", response);

        return Long.valueOf(response);
    }

    public String groupingQuestions(final GroupingQuestionReq questionList) throws JsonProcessingException {
        List<GptMessage> messages = new ArrayList<>();

        // gpt 역할(프롬프트) 설정
        messages.add(GptMessage.builder()
                .role(SYSTEM)
                .content(CHECK_SIMILARITY_PROMPT)
                .build());

        // 실제 요청
        messages.add(GptMessage.builder()
                .role(USER)
                .content(questionList.toJson())
                .build());

        log.info("Request Messages: {}", messages);

        HashMap<String, Object> requestBody = createRequestBody(messages);

        GptRes chatGptRes = getResponse(createHttpEntity(requestBody));

        String response = chatGptRes.getChoices().get(0).getMessage().getContent();
        log.info("Response: {}", response);

        return response;
    }

    // GPT 에 요청할 파라미터를 만드는 메서드
    private static HashMap<String, Object> createRequestBody(final List<GptMessage> messages) {
        HashMap<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", CHAT_MODEL);
        requestBody.put("messages", messages);
        requestBody.put("max_tokens", MAX_TOKEN);
        requestBody.put("temperature", TEMPERATURE);
        return requestBody;
    }

    // api 호출에 필요한 Http Header를 만들고 HTTP 객체를 만드는 메서드
    public HttpEntity<HashMap<String, Object>> createHttpEntity(final HashMap<String, Object> chatGptRequest){

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.parseMediaType(MEDIA_TYPE));
        httpHeaders.add(AUTHORIZATION, BEARER + apiKey);
        return new HttpEntity<>(chatGptRequest, httpHeaders);
    }

    // GPT API 요청후 response body를 받아오는 메서드
    public GptRes getResponse(final HttpEntity<HashMap<String, Object>> httpEntity){

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        // 답변이 길어질 경우 TimeOut Error 발생하므로 time 설정
        requestFactory.setConnectTimeout(60000);
        requestFactory.setReadTimeout(60000);   //  1min

        restTemplate.setRequestFactory(requestFactory);
        ResponseEntity<GptRes> responseEntity = restTemplate.exchange(
                CHAT_URL,
                HttpMethod.POST,
                httpEntity,
                GptRes.class);

        return responseEntity.getBody();
    }
}
