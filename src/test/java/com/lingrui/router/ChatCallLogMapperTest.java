package com.lingrui.router;

import com.lingrui.router.entity.ChatCallLog;
import com.lingrui.router.mapper.ChatCallLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
        "router.ark.api-key=local-test",
        "router.deepseek.api-key=local-test"
})
@Transactional
public class ChatCallLogMapperTest {



        @Autowired
        private ChatCallLogMapper chatCallLogMapper;
        @Autowired
        private JdbcTemplate jdbcTemplate;

        @Test
        void shouldInsertcallLog(){
                //1.准备一条模拟调用记录
                ChatCallLog record = new ChatCallLog();
                record.setRequestId("test-" + UUID.randomUUID());
                record.setModelAlias("doubao-chat");
                record.setChannel("ark");
                record.setStream(false);
                record.setPromptTokens(20L);
                record.setCompletionTokens(30L);
                record.setTotalTokens(50L);
                record.setStatus("SUCCESS");
                record.setDurationMs(1000L);
                record.setCreatedAt(LocalDateTime.now());

                //2.使用Mapper写入数据库
                int rows = chatCallLogMapper.insert(record);

                //3.检查插入行数和数据库生成的编号
                assertEquals(1, rows);
                assertNotNull(record.getId());

                //4.从数据库堵回来，确认数据确实写入
                Long saveTotalTokens = jdbcTemplate.queryForObject(
                        "SELECT total_tokens FROM chat_call_log WHERE id = ?",
                        Long.class,
                        record.getId()
                );

                assertEquals(Long.valueOf(50L), saveTotalTokens);
        }

}
