package desk.db;

public class DbException extends RuntimeException {
    public DbException(Throwable cause) {
        super(cause.getMessage(), cause);
    }
}
