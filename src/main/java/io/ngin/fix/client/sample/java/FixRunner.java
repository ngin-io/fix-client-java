package io.ngin.fix.client.sample.java;

import quickfix.DefaultMessageFactory;
import quickfix.FileStoreFactory;
import quickfix.Initiator;
import quickfix.LogFactory;
import quickfix.ScreenLogFactory;
import quickfix.SessionSettings;
import quickfix.SocketInitiator;

import java.io.FileInputStream;
import java.io.InputStream;

public class FixRunner {

	public static void main(String[] args) throws Exception {
		InputStream inputStream;
		if (args.length > 0) {
			inputStream = new FileInputStream(args[0]);
		} else {
			inputStream = FixRunner.class.getClassLoader().getResourceAsStream("fixclient.cfg");
		}

		try {

			if (inputStream == null) {
				return;
			}

			SessionSettings settings = new SessionSettings(inputStream);

			FixClientApplication application = new FixClientApplication(settings);

			FileStoreFactory storeFactory = new FileStoreFactory(settings);

			LogFactory logFactory = new ScreenLogFactory(settings);

			Initiator initiator = new SocketInitiator(application, storeFactory, settings,
					logFactory, new DefaultMessageFactory());

			initiator.start();

			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				System.out.println("Stopping initiator");
				initiator.stop();
			}));

			while (true) {
				sleep(1000);
			}
		} finally {
			if (inputStream != null) {
				inputStream.close();
			}
		}
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			System.err.println("Failed to sleep. " + e.getMessage());
		}
	}
}
