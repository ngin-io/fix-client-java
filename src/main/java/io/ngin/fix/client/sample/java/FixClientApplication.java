package io.ngin.fix.client.sample.java;


import quickfix.Application;
import quickfix.ConfigError;
import quickfix.FieldNotFound;
import quickfix.IncorrectTagValue;
import quickfix.IntField;
import quickfix.Message;
import quickfix.MessageCracker;
import quickfix.SessionID;
import quickfix.SessionNotFound;
import quickfix.SessionSettings;
import quickfix.StringField;
import quickfix.UnsupportedMessageType;
import quickfix.UtcTimeStampField;
import quickfix.field.ClOrdID;
import quickfix.field.ExecType;
import quickfix.field.MsgSeqNum;
import quickfix.field.MsgType;
import quickfix.field.OrdType;
import quickfix.field.RawData;
import quickfix.field.RawDataLength;
import quickfix.field.SendingTime;
import quickfix.fix44.ExecutionReport;
import quickfix.fix44.Heartbeat;
import quickfix.fix44.Logon;
import quickfix.fix44.Reject;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class FixClientApplication extends MessageCracker implements Application {

	private static final String CLORD_ID_PREFIX = "ID-";
	private static final String INV_ORD_CREATE_SUFFIX = "InvalidOrderCreate";
	private static final String INV_ORD_CANCEL_SUFFIX = "InvalidOrderCancel";

	private static final long SLEEP_TIME = 2000L;

	private static Map<String, String> senderCompToPrivetApiKeyMap = new HashMap<>();
	private static Set<String> rejectedOrderSet = new HashSet<>();

	private ClOrdID ordClOrdId;
	private ClOrdID invalidCreateOrdClOrdId;
	private ClOrdID invalidCancelOrdClOrdId;
	private final ExecutorService executor;

	public FixClientApplication(SessionSettings settings) {
		setSessions(settings);
		this.executor = Executors.newSingleThreadExecutor();
	}

	public void fromAdmin(Message message, SessionID sessionId) {
	}

	public void fromApp(Message message, SessionID sessionId) throws UnsupportedMessageType, IncorrectTagValue, FieldNotFound {
		crack(message, sessionId);
	}

	public void onCreate(SessionID sessionId) {
		System.out.println("--------- on Session Create ---------");
	}

	public void onLogon(SessionID sessionId) {
		System.out.printf("--------- Logon -%s --------- \n", sessionId);
		if (!rejectedOrderSet.isEmpty()) {
			return;
		}
		ordClOrdId = new ClOrdID(CLORD_ID_PREFIX + currentTimestampInString());
		invalidCreateOrdClOrdId = new ClOrdID(currentTimestampInString() + "-" + INV_ORD_CREATE_SUFFIX);
		invalidCancelOrdClOrdId = new ClOrdID(currentTimestampInString() + "-" + INV_ORD_CANCEL_SUFFIX);
		sleep(SLEEP_TIME);
		executor.execute(() -> sendCreateOrder(sessionId, ordClOrdId));
	}

	public void onLogout(SessionID sessionId) {
		System.out.printf("--------- Logout - %s --------- \n", sessionId);
	}

	public void toAdmin(Message message, SessionID sessionId) {
		try {
			crack(message, sessionId);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void toApp(Message message, SessionID sessionId) {
	}

	@Handler
	public void onMessage(Logon logon, SessionID sessionId) throws FieldNotFound {
		doLogon(logon, sessionId);
	}

	@Handler
	public void onMessage(Message message, SessionID sessionId) {
		System.out.printf("---------  Received message: [%s] ---------\n", message.getClass().getSimpleName());
	}

	@Handler
	public void onMessage(Reject message, SessionID sessionId) throws FieldNotFound {
		System.out.printf("---------  Received order reject. Order: SeqNumber :[%s]. Reason: [%s] ---------\n",
				message.getHeader().getString(MsgSeqNum.FIELD), message.getString(58));
	}

	@Handler
	public void onMessage(Heartbeat heartbeat, SessionID sessionId) throws FieldNotFound {
		System.out.printf("--------- Heartbeat --------- SenderCompID: %s, SendTime: %s\n", sessionId.getSenderCompID(),
				heartbeat.getHeader().getUtcTimeStamp(SendingTime.FIELD));
		Set<String> orderTypes = rejectedOrderSet.stream().map(v -> v.split("-")[1]).collect(Collectors.toSet());
		if (!orderTypes.contains(INV_ORD_CREATE_SUFFIX)) {
			executor.execute(() -> sendCreateInvalidOrder(sessionId, invalidCreateOrdClOrdId));
			rejectedOrderSet.add(invalidCreateOrdClOrdId.getValue());
		} else if (!orderTypes.contains(INV_ORD_CANCEL_SUFFIX)) {
			executor.execute(() -> sendCancelOrder(sessionId, invalidCancelOrdClOrdId));
			rejectedOrderSet.add(invalidCancelOrdClOrdId.getValue());
		} else {
			System.out.println("--------- Terminating app...");
			executor.execute(() -> {
				executor.shutdownNow();
				System.exit(0);
			});
		}
	}

	@Handler
	public void onMessage(ExecutionReport order, SessionID sessionId) throws FieldNotFound {

		if (!rejectedOrderSet.isEmpty()) {
			return;
		}

		if (order.getExecType().valueEquals(ExecType.CANCELED)) {
			System.out.printf("--------- Received execution report for cancel order, Id: %s, Status: %s\n", order.getClOrdID().getValue(), order.getOrdStatus().getValue());
			return;
		}

		if (order.getExecType().valueEquals(ExecType.ORDER_STATUS)) {
			System.out.printf("--------- Received execution report for order status: %s\n", order.getOrdStatus().getValue());
			sleep(SLEEP_TIME);
			executor.execute(() -> sendCancelOrder(sessionId, ordClOrdId));
			return;
		}

		if (!order.getExecType().valueEquals(ExecType.NEW)
				&& !order.getExecType().valueEquals(ExecType.PARTIAL_FILL)
				&& !order.getExecType().valueEquals(ExecType.TRADE)) {
			System.out.printf("--------- Received unexpected execution report %s from gateway \n", order.getExecType());

		}

		if (order.getOrdType().valueEquals(OrdType.MARKET)) {
			System.out.printf("--------- Received execution report for market order, Id: %s, Status: %s\n", order.getClOrdID().getValue(), order.getOrdStatus().getValue());
			sleep(SLEEP_TIME);
			executor.execute(() -> sendOrderStatus(sessionId, ordClOrdId));
		} else {
			System.out.printf("--------- Received execution report for limit order, Id: %s, Status: %s\n", order.getClOrdID().getValue(), order.getOrdStatus().getValue());
			sleep(SLEEP_TIME);
			executor.execute(() -> sendOrderStatus(sessionId, ordClOrdId));
		}
	}

	private static void doLogon(Logon message, SessionID sessionId) throws FieldNotFound {
		String msgType = message.getHeader().getString(MsgType.FIELD);
		if (!Logon.MSGTYPE.equals(msgType)) {
			return;
		}

		String sendingTime = getSendingTime(message);
		String senderCompID = sessionId.getSenderCompID();

		String signedMessage = CryptoUtils.signMessage(1, Logon.MSGTYPE, senderCompID, sendingTime,
				senderCompToPrivetApiKeyMap.get(senderCompID));

		message.setField(new StringField(RawData.FIELD, signedMessage));
		message.setField(new IntField(RawDataLength.FIELD, signedMessage.length()));
	}

	private static void sendCreateOrder(SessionID sessionID, ClOrdID ordClOrdId) {
		try {
			boolean isSent = OrderClient.createOrder("BAT-AUD", "5", "0.1", '1', '2', ordClOrdId,
					sessionID);
			System.out.printf("Order create [%s] sent? %b\n", ordClOrdId, isSent);
		} catch (SessionNotFound e) {
			System.err.println("Failed to send create order. " + e.getMessage());
		}
	}

	private static void sendCreateInvalidOrder(SessionID sessionID, ClOrdID ordClOrdId) {
		try {
			boolean isSent = OrderClient.createOrder("BAT-AUD", "5", "10000000000000", '1', '2', ordClOrdId,
					sessionID);
			System.out.printf("Order create [%s] sent? %b\n", ordClOrdId, isSent);
		} catch (SessionNotFound e) {
			System.err.println("Failed to send create order. " + e.getMessage());
		}
	}

	private static void sendCancelOrder(SessionID sessionID, ClOrdID ordClOrdId) {
		try {
			ClOrdID newOrdClOrdId = new ClOrdID(CLORD_ID_PREFIX + currentTimestampInString()
					+ Thread.currentThread().getName());
			boolean isSent = OrderClient.cancelOrder(ordClOrdId, newOrdClOrdId, sessionID);
			System.out.printf("Order cancel [%s] sent? %b\n", ordClOrdId, isSent);
		} catch (SessionNotFound e) {
			System.err.println("Failed to send cancel order. " + e.getMessage());
		}
	}

	private static void sendOrderStatus(SessionID sessionID, ClOrdID ordClOrdId) {
		try {
			boolean isSent = OrderClient.sendOrderStatus(ordClOrdId,
					sessionID);
			System.out.printf("Order status [%s] sent? %b\n", ordClOrdId, isSent);
		} catch (SessionNotFound e) {
			System.err.println("Failed to send order status. " + e.getMessage());
		}
	}

	private void setSessions(SessionSettings settings) {
		try {
			Iterator<SessionID> sessionIter = settings.sectionIterator();
			while (sessionIter.hasNext()) {
				SessionID session = sessionIter.next();
				String privateKey = (String) settings.getSessionProperties(session).get("PrivateKey");
				senderCompToPrivetApiKeyMap.put(session.getSenderCompID(), privateKey);
			}
		} catch (ConfigError e) {
			throw new RuntimeException(e);
		}
	}

	private static String getSendingTime(Message message) {
		String sendingTime = Long.toString(System.currentTimeMillis());
		try {
			LocalDateTime dateTime = message.getHeader().getField(new UtcTimeStampField(SendingTime.FIELD)).getValue();
			return Long.toString(dateTime.toInstant(ZoneOffset.UTC).toEpochMilli());
		} catch (FieldNotFound e) {
			return sendingTime;
		}
	}

	private static String currentTimestampInString() {
		LocalDateTime dateTime = LocalDateTime.now();
		return Long.toString(dateTime.toInstant(ZoneOffset.UTC).toEpochMilli());

	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			System.err.println("Failed to sleep. " + e.getMessage());
		}
	}
}