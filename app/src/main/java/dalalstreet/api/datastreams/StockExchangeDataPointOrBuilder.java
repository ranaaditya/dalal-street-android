// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: datastreams/StockExchange.proto

package dalalstreet.api.datastreams;

public interface StockExchangeDataPointOrBuilder extends
    // @@protoc_insertion_point(interface_extends:proto.StockExchangeDataPoint)
    com.google.protobuf.MessageLiteOrBuilder {

  /**
   * <code>optional uint32 price = 1;</code>
   */
  int getPrice();

  /**
   * <code>optional uint32 stocks_in_exchange = 2;</code>
   */
  int getStocksInExchange();

  /**
   * <code>optional uint32 stocks_in_market = 3;</code>
   */
  int getStocksInMarket();
}