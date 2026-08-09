package com.cnealgithub.springAiTut.Tools;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.i18n.LocaleContextHolder;

import java.time.LocalDateTime;

@RequiredArgsConstructor
public class SimpleDateTimeTool {

    private Logger logger = LoggerFactory.getLogger(SimpleDateTimeTool.class);

    @Tool(description = "Tool for getting the current date and time ")
    public String getCurrentDateTime(){
        logger.info("simple date and time tool called");
        return LocalDateTime
                .now()
                .atZone(LocaleContextHolder.getTimeZone().toZoneId())
                .toString();
    }
}
