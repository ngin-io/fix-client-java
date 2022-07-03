package io.ngin.fix.client.sample.java;

import quickfix.Session;
import quickfix.SessionID;
import quickfix.SessionNotFound;
import quickfix.field.ClOrdID;
import quickfix.field.OrdType;
import quickfix.field.OrderQty;
import quickfix.field.OrigClOrdID;
import quickfix.field.Price;
import quickfix.field.Side;
import quickfix.field.Symbol;
import quickfix.field.TimeInForce;
import quickfix.fix44.NewOrderSingle;
import quickfix.fix44.OrderCancelRequest;
import quickfix.fix44.OrderStatusRequest;

public class OrderClient {

	public static boolean createOrder(String markerId, String priceStr, String quantityStr, char sideCh, char orderTypeCh,
									  ClOrdID ordClOrdId, SessionID sessionId) throws SessionNotFound {
		NewOrderSingle orderSingle = buildOrder(markerId, priceStr, quantityStr, sideCh, orderTypeCh, ordClOrdId);
		return Session.sendToTarget(orderSingle, sessionId);
	}

	public static boolean sendOrderStatus(ClOrdID ordClOrdId, SessionID sessionId) throws SessionNotFound {
		OrderStatusRequest request = new OrderStatusRequest();
		request.set(ordClOrdId);
		return Session.sendToTarget(request, sessionId);
	}


	public static boolean cancelOrder(ClOrdID ordClOrdId, ClOrdID newOrdClOrdId, SessionID sessionId) throws SessionNotFound {
		OrderCancelRequest request = new OrderCancelRequest();
		request.set(newOrdClOrdId);
		OrigClOrdID origClOrdId = new OrigClOrdID(ordClOrdId.getValue());
		request.set(origClOrdId);
		return Session.sendToTarget(request, sessionId);
	}

	private static NewOrderSingle buildOrder(String markerId, String priceStr, String quantityStr, char sideCh, char orderTypeCh,
											 ClOrdID ordClOrdId) {

		NewOrderSingle order = new NewOrderSingle();
		Symbol symbol = new Symbol(markerId);
		OrdType ordType = new OrdType(orderTypeCh);
		Price price = new Price(Double.parseDouble(priceStr));
		Side side = new Side(sideCh);
		TimeInForce tif = new TimeInForce('1');
		OrderQty quantity = new OrderQty(Double.parseDouble(quantityStr));

		order.set(symbol);
		order.set(ordType);
		if (ordType.getValue() == OrdType.LIMIT) {
			order.set(price);
		}
		order.set(side);
		order.set(tif);
		order.set(quantity);
		order.set(ordClOrdId);

		return order;
	}
}
