package com.lingrui.router.vo;

import lombok.Data;

@Data
public class ChatCallStatsVO {

    private Long totalCount;

    private Long successCount;

    private Long failedCount;
}
