package com.impulselock.impulselock.repository;

import com.impulselock.impulselock.model.Transaction;
import java.sql.Timestamp;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class TransactionRepository {

    private static final Logger log = LoggerFactory.getLogger(TransactionRepository.class);

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Transaction> transactionRowMapper = (rs, rowNum) -> {
        Transaction transaction = new Transaction();
        transaction.setTransactionId(rs.getString("transaction_id"));
        transaction.setUserId(rs.getString("user_id"));
        transaction.setAmount(rs.getDouble("amount"));
        transaction.setCategory(rs.getString("category"));
        transaction.setMerchant(rs.getString("merchant"));
        Timestamp timestamp = rs.getTimestamp("timestamp");
        if (timestamp != null) {
            transaction.setTimestamp(timestamp.toLocalDateTime());
        }
        return transaction;
    };

    public TransactionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveTransaction(Transaction transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("transaction must not be null");
        }
        if (transaction.getTransactionId() == null || transaction.getTransactionId().isBlank()) {
            throw new IllegalArgumentException("transactionId must not be null/blank");
        }

        Timestamp ts = (transaction.getTimestamp() == null) ? null : Timestamp.valueOf(transaction.getTimestamp());

        log.debug(
                "Saving transaction to DB: transactionId={}, userId={}, amount={}, category={}, merchant={}, timestamp={}",
                transaction.getTransactionId(),
                transaction.getUserId(),
                transaction.getAmount(),
                transaction.getCategory(),
                transaction.getMerchant(),
                transaction.getTimestamp()
        );

        // `timestamp` is a reserved keyword in MySQL; escape it to avoid SQL syntax errors.
        String sql = "INSERT INTO transactions (transaction_id, user_id, amount, category, merchant, `timestamp`) VALUES (?, ?, ?, ?, ?, ?)";

        try {
            jdbcTemplate.update(
                    sql,
                    transaction.getTransactionId(),
                    transaction.getUserId(),
                    transaction.getAmount(),
                    transaction.getCategory(),
                    transaction.getMerchant(),
                    ts
            );
        } catch (DataAccessException exception) {
            log.error(
                    "DB insert failed for transactionId={}, userId={}. Cause: {}",
                    transaction.getTransactionId(),
                    transaction.getUserId(),
                    exception.getMessage(),
                    exception
            );
            throw exception;
        }
    }

    public List<Transaction> getTransactionsByUserId(String userId) {
        String sql = "SELECT transaction_id, user_id, amount, category, merchant, `timestamp` FROM transactions WHERE user_id = ? ORDER BY `timestamp` DESC";
        return jdbcTemplate.query(sql, transactionRowMapper, userId);
    }
}
