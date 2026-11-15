package com.kdrinker.analysis.label;

import com.kdrinker.media.FocusBox;

/** index번째(0부터, 입력 사진 순서) 사진에서 병을 감싸는 상자. */
public record PhotoBox(int index, FocusBox box) {
}
