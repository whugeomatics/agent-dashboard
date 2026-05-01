package local.agent.dashboard;

final class BadRequestException extends RuntimeException {
    BadRequestException(String message) {
        super(message);
    }
}
