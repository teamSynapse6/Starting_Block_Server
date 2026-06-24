package com.startingblock.domain.mail.application;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ContactEmailExtractor {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,20}");

    private ContactEmailExtractor() {
    }

    public static Optional<String> extract(final String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = EMAIL_PATTERN.matcher(normalize(text));
        return matcher.find() ? Optional.of(matcher.group()) : Optional.empty();
    }

    private static String normalize(final String text) {
        return text
                .replace("mailto:", " ")
                .replace("＠", "@")
                .replaceAll("(?i)\\bE\\s*[- ]?\\s*mail\\s*[.:]?\\s*", " ")
                .replaceAll("(?i)\\bE\\s*[.:]\\s*", " ")
                .replaceAll("(?i)\\s*\\(?\\s*at\\s*\\)?\\s*", "@")
                .replaceAll("(?i)\\s*\\[\\s*at\\s*]\\s*", "@")
                .replaceAll("\\s*골뱅이\\s*", "@")
                .replaceAll("(?i)\\s*\\(?\\s*dot\\s*\\)?\\s*", ".")
                .replaceAll("(?i)\\s*\\[\\s*dot\\s*]\\s*", ".")
                .replaceAll("\\s*점\\s*", ".")
                .replaceAll("\\s*@\\s*", "@")
                .replaceAll("\\s*\\.\\s*", ".");
    }
}
