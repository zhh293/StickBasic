package com.tmd.mapper;

import com.tmd.entity.PaymentRefund;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentRefundMapper {
    int insert(PaymentRefund refund);
    int updateStatus(@Param("refundNo") String refundNo, @Param("status") String status);
    PaymentRefund findByRefundNo(@Param("refundNo") String refundNo);
}