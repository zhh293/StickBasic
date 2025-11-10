package com.tmd.mapper;

import com.tmd.entity.PaymentOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PaymentOrderMapper {
    int insert(PaymentOrder order);
    int updateStatus(@Param("orderNo") String orderNo, @Param("status") String status);
    int updateOnPaid(@Param("orderNo") String orderNo,
                     @Param("transactionId") String transactionId);
    PaymentOrder findByOrderNo(@Param("orderNo") String orderNo);
    List<PaymentOrder> findByUserId(@Param("userId") Long userId);
}