package com.remockable.api.model.enums;

/** ACTIVE 為有效回答；分析完成前重新送出會把舊的標為 SUPERSEDED（Spec §17.2）。 */
public enum AttemptState {
    ACTIVE, SUPERSEDED
}
