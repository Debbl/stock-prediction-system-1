package com.stock.util;

import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * @author admin
 * @description
 * @since 2023-03-31
 */
@Component
public class GetCurrentTime {
    public String getCurrentTimeByDay() {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        Date date = new Date(System.currentTimeMillis());
        return formatter.format(date);
    }

    public boolean inOneMonth(String date, String time) {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        LocalDate localDate1 = LocalDate.parse(time, dateTimeFormatter);
        LocalDate plus = localDate1.plus(1, ChronoUnit.MONTHS);
        LocalDate localDate2 = LocalDate.parse(date, dateTimeFormatter);
        return plus.isAfter(localDate2);
    }
}
