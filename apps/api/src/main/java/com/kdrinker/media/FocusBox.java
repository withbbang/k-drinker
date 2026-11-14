package com.kdrinker.media;

/** 사진에서 강조할 영역. 모든 값은 원본 크기에 대한 0~1 비율이다. */
public record FocusBox(double x, double y, double w, double h) {

}
