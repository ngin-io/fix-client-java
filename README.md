# fix-client-java

Sample application demonstrating how to connect to interact with BTC Markets' FIX engine using Java application. 

## Configuration
Configuration file is located at `resources/fix/fixclient.cfg`

1) Set connection host:  `SocketConnectHost=fix.btcmarkets.net`
2) Set SenderCompID:     `SenderCompID=Public API key genereteat at BTC Markets website`
3) Set PrivateKey:       `PrivateKey=Secrte API key genereteat at BTC Markets website`

## Build

`mvn clean install`

This application has been tested with JDK 17 

## Run

`mvn exec:java -Dexec.mainClass=io.ngin.fix.client.sample.java.FixRunner`

Argument `-Dexec.args=/path-to/fixclient.cfg` can be passed to override FIX configuration.

## Basic flow

1) Application `Logon` to FIX server -> `--------- Logon -FIX.4.4:PUBLIC_API_KEY->BTCM --------- `. Sleep 2 seconds
2) On successful logon `limit order` is created -> `--------- Received execution report for limit order, Id: ID-1656093191324, Status: 0`. Sleep 2 seconds
3) On successful order creation initiated `order status` request -> `--------- Received execution report for order status: 0`. Sleep 2 seconds
4) On successful order status initiated `order cancel` request -> `--------- Received execution report for cancel order, Id: ID-1656093191324, Status: 4`. Sleep 2 seconds
5) After this application waits for Heartbeat (configured in property file `HeartBtInt=10`) -> `--------- Heartbeat --------- SenderCompID: 7cfaa640-abcd-405a-9dea-b0030134033b, SendTime: 2022-06-24T15:53:29.840`
6) Heartbeat received -> `SenderCompID: PUBLIC_API_KEY, SendTime: 2022-06-23T19:56:09.221`.
7) On the heartbeat send order with invalid parameters to simulate order reject -> `Received order reject. Order: SeqNumber :[7]. Reason: [Value is incorrect (out of range) for this tag, field=103] `
8) On the next heartbeat send cancel order with non-existing ID -> `---------  Received order reject. Order: SeqNumber :[10]. Reason: [Required tag missing, field=37] ---------`
9) On the upcoming heartbeat shutting down initiator. Before logout is initiated -> `---------  Received message: [Logout] ---------`

