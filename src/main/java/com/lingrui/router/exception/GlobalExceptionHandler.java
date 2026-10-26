package com.lingrui.router.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;

/**
 * 集中处理接口异常，将错误说明转换为JSON响应
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理主动抛出的 HTTP 状态异常
     * @param exception 请求处理过程中抛出的异常
     * @return 包含对应 HTTP 状态码和错误说明的响应
     */
    @ExceptionHandler(ResponseStatusException.class) //负责管理ResponseStatusException这一类异常
    public ResponseEntity<Map<String, Object>> handleStatusException(ResponseStatusException exception) {

        //内层对象，保存错误说明
        Map<String, Object> error = new HashMap<>();
        error.put("message" ,exception.getReason());

        //外层对象，保存错误码和内层对象
        Map<String, Object> body = new HashMap<>();
        body.put("error", error);

        return ResponseEntity
                .status(exception.getStatusCode())
                .body(body);
    }

    /**
     *处理上游平台返回的 HTTP 错误
     * @param exception 调用上游时产生的异常响应
     * @return 统一格式的上游错误响应
     */
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<Map<String, Object>> handleUpstreamException(RestClientResponseException exception) {

        //获取上游返回的状态码
        int upstreamStatus = exception.getStatusCode().value();//因为StatusCode是一个对象，需要调用value()方法获取其中的int值

        //默认错误说明
        String message = "上游模型服务调用失败，请稍后重试";

        if(upstreamStatus == 401 || upstreamStatus == 403){
            message = "上游渠道认证或权限异常，请联系中转站管理员";
        }else if(upstreamStatus == 404){
            message = "上游模型或接口不可用，请联系中转站管理员";
        }else if(upstreamStatus == 429){
            message = "上游服务请求受限，请稍后重试";
        }else if(upstreamStatus == 500){
            message = "上游服务器暂时无法接受请求，请稍后重试";
        }

        //组织内层错误信息
        Map<String , Object> error = new HashMap<>();
        error.put("code" ,"upstream_error");
        error.put("message" ,message);
        error.put("upstream_status", upstreamStatus);

        //组织外层响应
        Map<String, Object> body = new HashMap<>();
        body.put("error", error);

        //当前阶段，上游 HTTP 错误统一向调用者返回502
        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(body);

    }

    /**
     * 处理与上游通信时发生的连接或读写错误
     * @param exception 上游通信异常
     * @return 统一格式的通信失败响应
     */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<Map<String, Object>> handleUpstreamConnectionException(ResourceAccessException exception){

        boolean timeout = isTimeout(exception);

        Map<String, Object> error = new HashMap<>();
        HttpStatus status;

        if(timeout){
            error.put("code", "upstream_timeout");
            error.put("message", "等待上游模型服务响应超时，请稍后重试");
            status = HttpStatus.GATEWAY_TIMEOUT;
        } else{
            error.put("code", "upstream_connection_error");
            error.put("message" ,"与上游模型服务通信失败，请稍后重试");
            status = HttpStatus.BAD_GATEWAY;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("error", error);

        return ResponseEntity
                .status(status)
                .body(body);
    }

    /**
     * 检查异常及其内部原因，是否包含网络超时
     */
    private boolean isTimeout(Throwable exception) {
        Throwable current = exception;

        while (current != null){
            if(current instanceof SocketTimeoutException){
                return true;
            }

            current = current.getCause();
        }

        return false;
    }
}
