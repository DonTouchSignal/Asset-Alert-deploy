package com.example.msaasset.insert;

import com.opencsv.CSVReader;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStreamReader;
import java.sql.PreparedStatement;
import java.util.List;

@Service  // ✅ Spring Bean으로 등록
public class CSVtoDatabaseService {

    private final JdbcTemplate jdbcTemplate;

    @Value("${csv.domestic.path:1국내주식탑100.csv}")  // ✅ application.properties에서 경로 설정 가능
    private String domesticCsvFile;

    @Value("${csv.foreign.path:1해외주식탑100.csv}")
    private String foreignCsvFile;

    public CSVtoDatabaseService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct // ✅ 애플리케이션 시작 시 실행
    public void init() {
        insertCSVToDatabase(domesticCsvFile, "KOSPI", 1, true);
        insertCSVToDatabase(foreignCsvFile, "NASDAQ", 2, false);
    }

    private void insertCSVToDatabase(String csvFile, String market, int categoryId, boolean isDomestic) {
        String sql = "INSERT INTO asset (symbol, korean_name, english_name, market, category_id, created_at) " +
                "VALUES (?, ?, ?, ?, ?, NOW())";

        try (CSVReader reader = new CSVReader(new InputStreamReader(new ClassPathResource(csvFile).getInputStream()))) {
            List<String[]> lines = reader.readAll();
            lines.remove(0); // 첫 줄(헤더) 제거

            jdbcTemplate.batchUpdate(sql, lines, 100, (PreparedStatement ps, String[] line) -> {
                if (line.length < 3) return;

                String symbol = line[0];
                String name = isDomestic ? line[2] : line[1];

                if (isDomestic) {
                    symbol = String.format("%06d", Integer.parseInt(symbol)); // 국내 주식 6자리 코드 보정
                }

                ps.setString(1, symbol);
                if (market.equals("KOSPI")) {
                    ps.setString(2, name);
                    ps.setNull(3, java.sql.Types.VARCHAR);
                } else {
                    ps.setNull(2, java.sql.Types.VARCHAR);
                    ps.setString(3, name);
                }
                ps.setString(4, market);
                ps.setInt(5, categoryId);
            });

            System.out.println("✅ " + market + " 데이터 삽입 완료!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
