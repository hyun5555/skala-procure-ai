package com.lecture.course.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * description 의 `계약기간: 20240131~20250331` 에서 종료일을 뽑는다.
 *
 * **왜 서버가 뽑는가.** 종료일을 클라이언트가 보내게 하면 호출자마다 따로 고쳐야 하고,
 * 앞으로 생길 세 번째 호출자가 잊으면 조용히 빠진다. 그러면 만료 필터가 아무것도
 * 거르지 않는데 아무도 눈치채지 못한다. description 은 모든 호출자가 이미 보내고
 * 있고 그 안에 계약기간이 들어 있으므로 여기서 한 번만 뽑는 편이 안전하다.
 *
 * 형식이 다르면 null 을 돌려준다. 만료 판정을 포기하는 것이 임의로 날짜를 지어내는
 * 것보다 낫다 — 판정할 수 없는 품목은 목록에 남는다.
 */
final class ContractPeriodParser {

    // 계약기간: 20240131~20250331   (공백과 구분자 흔들림을 허용한다)
    private static final Pattern PATTERN =
            Pattern.compile("계약기간\\s*:\\s*(\\d{8})\\s*[~\\-]\\s*(\\d{8})");
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    private ContractPeriodParser() {
    }

    static LocalDate endDateOf(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        Matcher matcher = PATTERN.matcher(description);
        if (!matcher.find()) {
            return null;
        }
        try {
            return LocalDate.parse(matcher.group(2), YYYYMMDD);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
