package com.github.copilot.tray.sdk;

import com.github.copilot.sdk.events.*;
import com.github.copilot.tray.session.SessionManager;
import com.github.copilot.tray.session.SessionStatus;
import com.github.copilot.tray.session.SubagentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Routes SDK events from a specific session to SessionManager state mutations.
 */
public class EventRouter {

    private static final Logger LOG = LoggerFactory.getLogger(EventRouter.class);

    private final SessionManager sessionManager;

    public EventRouter(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     * Route an SDK event for the given session to the appropriate SessionManager method.
     */
    public void route(String sessionId, AbstractSessionEvent event) {
        LOG.debug("Event for session {}: {}", sessionId, event.getClass().getSimpleName());

        switch (event) {
            case SessionStartEvent e ->
                    sessionManager.setStatus(sessionId, SessionStatus.ACTIVE);

            case SessionShutdownEvent e ->
                    sessionManager.archiveSession(sessionId);

            case SessionIdleEvent e ->
                    sessionManager.setStatus(sessionId, SessionStatus.IDLE);

            case SessionErrorEvent e ->
                    sessionManager.setStatus(sessionId, SessionStatus.ERROR);

            case AssistantTurnStartEvent e ->
                    sessionManager.setStatus(sessionId, SessionStatus.BUSY);

            case AssistantTurnEndEvent e ->
                    sessionManager.setStatus(sessionId, SessionStatus.IDLE);

            case SessionUsageInfoEvent e -> {
                var data = e.getData();
                sessionManager.updateUsage(sessionId,
                        (int) data.currentTokens(),
                        (int) data.tokenLimit(),
                        (int) data.messagesLength());
            }

            case SessionModelChangeEvent e ->
                    sessionManager.updateModel(sessionId, extractModelName(e));

            case SubagentStartedEvent e ->
                    sessionManager.addSubagent(sessionId,
                            extractSubagentId(e), extractSubagentDescription(e));

            case SubagentCompletedEvent e ->
                    sessionManager.updateSubagent(sessionId,
                            extractSubagentId(e), SubagentStatus.COMPLETED);

            case SubagentFailedEvent e ->
                    sessionManager.updateSubagent(sessionId,
                            extractSubagentId(e), SubagentStatus.FAILED);

            case PermissionRequestedEvent e ->
                    sessionManager.setPendingPermission(sessionId, true);

            case PermissionCompletedEvent e ->
                    sessionManager.setPendingPermission(sessionId, false);

            default ->
                    LOG.trace("Unhandled event type for session {}: {}",
                            sessionId, event.getClass().getSimpleName());
        }
    }

    // Helper methods to extract data from events.
    // The SDK event data accessors vary; these safely extract what we need.

    private String extractModelName(SessionModelChangeEvent event) {
        try {
            var data = event.getData();
            if (data != null) {
                return data.toString();
            }
        } catch (Exception e) {
            LOG.debug("Could not extract model name from event", e);
        }
        return "unknown";
    }

    private String extractSubagentId(AbstractSessionEvent event) {
        try {
            var method = event.getClass().getMethod("getData");
            var data = method.invoke(event);
            if (data != null) {
                var idMethod = data.getClass().getMethod("id");
                return (String) idMethod.invoke(data);
            }
        } catch (Exception e) {
            LOG.debug("Could not extract subagent id", e);
        }
        return "unknown-subagent";
    }

    private String extractSubagentDescription(AbstractSessionEvent event) {
        try {
            var method = event.getClass().getMethod("getData");
            var data = method.invoke(event);
            if (data != null) {
                var descMethod = data.getClass().getMethod("description");
                return (String) descMethod.invoke(data);
            }
        } catch (Exception e) {
            LOG.debug("Could not extract subagent description", e);
        }
        return "";
    }
}
