package br.com.elosoft.teledyna;

import com.dynatrace.diagnostics.pdk.ActionEnvironment;
import com.dynatrace.diagnostics.pdk.ActionV2;
import com.dynatrace.diagnostics.pdk.Incident;
import com.dynatrace.diagnostics.pdk.Status;
import com.dynatrace.diagnostics.pdk.Violation;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.*;

import static java.net.URLEncoder.encode;
import static java.util.logging.Logger.getLogger;

public class TeleDyna implements ActionV2 {

	private static final Logger logger = getLogger(TeleDyna.class.getName());

	private static final Character NEWLINE = '\n';

	@Override
	public Status setup(ActionEnvironment env) throws Exception {
		return new Status(Status.StatusCode.Success);
	}

	/**
	 * Executes the Action Plugin to process incidents.
	 * <p>
	 * <p>
	 * This method may be called at the scheduled intervals, but only if incidents
	 * occurred in the meantime. If the Plugin execution takes longer than the
	 * schedule interval, subsequent calls to
	 * {@link #execute(ActionEnvironment)} will be skipped until this method
	 * returns. After the execution duration exceeds the schedule timeout,
	 * {@link ActionEnvironment#isStopped()} will return <tt>true</tt>. In this
	 * case execution should be stopped as soon as possible. If the Plugin
	 * ignores {@link ActionEnvironment#isStopped()} or fails to stop execution in
	 * a reasonable timeframe, the execution thread will be stopped ungracefully
	 * which might lead to resource leaks!
	 *
	 * @param env a <tt>ActionEnvironment</tt> object that contains the Plugin
	 *            configuration and incidents
	 * @return a <tt>Status</tt> object that describes the result of the
	 * method call
	 */
	@Override
	public Status execute(ActionEnvironment env) throws Exception {
		Collection<Incident> incidents = env.getIncidents();

		final String telegramPathShell = env.getConfigString("TELEGRAM_PATH_SHELL");
		final String telegramToken = env.getConfigString("TELEGRAM_TOKEN");
		final String telegramChat = env.getConfigString("TELEGRAM_CHAT");

		int nbFailures = 0;
		for (Incident incident : incidents) {
			sendMessage(telegramPathShell, telegramToken, telegramChat, getTelegramMessage(incident));
		}

		return new Status(getStatusCode(nbFailures, incidents.size()));
	}

	private Status.StatusCode getStatusCode(int nbFailures, int nbIncidents) {
		if (nbFailures == 0) {
			return Status.StatusCode.Success;
		}

		if (nbFailures != nbIncidents) {
			return Status.StatusCode.PartialSuccess;
		}

		return Status.StatusCode.ErrorInternalException;
	}

	private void sendMessage(String telegramPathShell, String token, String chat, String telegramMessage) throws IOException {
	    LocalShell shell = new LocalShell();
	    String cmd = telegramPathShell+"/telegram.sh -M -t " + token + " -c " + chat + " '"+telegramMessage+"'";
	    shell.executeCommand(cmd);
	}

	@SuppressWarnings("unchecked")
	private String getTelegramMessage(Incident incident) throws UnsupportedEncodingException {
		StringBuilder msg = new StringBuilder("");
		msg.append("*TELEDYNA NOTIFICATION*").append(NEWLINE);
		msg.append("*" + getTitle(incident) + " - [" + getSeverity(incident).toUpperCase() + "]*").append(NEWLINE);
		msg.append(getMessage(incident)).append(NEWLINE);
		return msg.toString();
	}

	private String getTitle(Incident incident) {
		return incident.getIncidentRule().getName();
	}

	private String getState(Incident incident) {
		String state = "";
		if (incident.isOpen()) {
			state = state + "Dynatrace incident triggered:";
		} else if (incident.isClosed()) {
			state = state + "Dynatrace incident ended:";
		}
		return state;
	}

	private String getMessage(Incident incident) {
		StringBuilder message = new StringBuilder();
		message.append("*Status:* ").append(getStatus(incident).toUpperCase()).append(NEWLINE);
		message.append("*Incident start:* ").append(incident.getStartTime()).append(NEWLINE);
		message.append("*Incident end:* ").append(incident.getEndTime()).append(NEWLINE);
		message.append("*Message:* ").append(incident.getMessage()).append(NEWLINE);
		for (Violation violation : incident.getViolations()) {
			message.append("*Violated Measure:* ").append(violation.getViolatedMeasure().getName()).append(" - Threshold: ").append(violation.getViolatedThreshold().getValue()).append(NEWLINE);
		}
		return message.toString();
	}

	private String getSeverity(Incident incident) {
		return incident.getSeverity().toString();
	}

	private String getStatus(Incident incident) {
		String status = "";
		if (incident.isOpen()) {			
			status += "Open";
		} else {
			status += "Closed";
		}
		return status;
	}	

	@Override
	public void teardown(ActionEnvironment env) throws Exception {
		// not doing anything here...
	}
}