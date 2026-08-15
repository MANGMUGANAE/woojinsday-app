package com.daejin.woojintoday.data.model

data class GenEdArea(val code: String, val label: String)

/** 교선(교양선택) 세부영역 19개 — SyllabusClient의 인덱스 구축과 영역 필터 UI가 이 목록을 공유한다. */
object GenEdAreas {
    val ALL = listOf(
        GenEdArea("B42001", "1영역"),
        GenEdArea("B42002", "2영역"),
        GenEdArea("B42003", "3영역"),
        GenEdArea("B42004", "4영역"),
        GenEdArea("B42005", "5영역"),
        GenEdArea("B42006", "6영역"),
        GenEdArea("B42007", "7영역"),
        GenEdArea("B42008", "8영역"),
        GenEdArea("B42009", "9영역"),
        GenEdArea("B4200A", "A영역"),
        GenEdArea("B4200B", "B영역"),
        GenEdArea("B4200C", "C영역"),
        GenEdArea("B42030", "균형교양"),
        GenEdArea("B42031", "일반교양"),
        GenEdArea("B42020", "핵심교양"),
        GenEdArea("B42021", "자율선택교양"),
        GenEdArea("B42010", "실용교양영역"),
        GenEdArea("B42011", "외국어영역"),
        GenEdArea("B42012", "심화교양영역")
    )
}
