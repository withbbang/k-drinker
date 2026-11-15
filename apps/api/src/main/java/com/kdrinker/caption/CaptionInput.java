package com.kdrinker.caption;

import com.kdrinker.analysis.label.LabelResult;
import com.kdrinker.brewery.entity.Brewery;
import java.math.BigDecimal;

/** 캡션 입력. brewery는 못 찾았으면 null. */
public record CaptionInput(LabelResult label, BigDecimal rating, Brewery brewery, String memo) {
}
