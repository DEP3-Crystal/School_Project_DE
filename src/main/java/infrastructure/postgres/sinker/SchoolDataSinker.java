package infrastructure.postgres.sinker;

import utils.FileUtils;
import model.School;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.io.jdbc.JdbcIO;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollection;

import java.io.IOException;
import java.util.Properties;

public class SchoolDataSinker {
    public static final String CSV_HEADER = "school_id,location,name";

    public static void main(String[] args) throws IOException {

        Properties properties = FileUtils.loadProperties("database.properties");

        String driver = properties.getProperty("PGDRIVER");
        String hostName = properties.getProperty("PGHOSTNAME");
        String username = properties.getProperty("PGUSERNAME");
        String password = properties.getProperty("PGPASSWORD");
        String tableName = "school";

        PipelineOptions options = PipelineOptionsFactory.create();
        Pipeline pipeline = Pipeline.create(options);

        PCollection<School> data = pipeline
                .apply("Read data from csv file", TextIO.read().from("src/main/resources/input_data/school_table.csv"))
                .apply(ParDo.of(new FilterHeaderFn(CSV_HEADER)))
                .apply(ParDo.of(new ParseSchoolDataFn()));

        data.apply(JdbcIO.<School>write().withDataSourceConfiguration(JdbcIO.DataSourceConfiguration
                        .create(driver, hostName)
                        .withUsername(username)
                        .withPassword(password))
                .withStatement(String.format("insert into %s values(?, ?, ?)", tableName))
                .withPreparedStatementSetter((JdbcIO.PreparedStatementSetter<School>) (element, preparedStatement) -> {

                    preparedStatement.setInt(1, element.getSchoolId());
                    preparedStatement.setString(2, element.getLocation());
                    preparedStatement.setString(3, element.getName());
                }));

        pipeline.run().waitUntilFinish();
    }

    public static class FilterHeaderFn extends DoFn<String, String> {

        private final String header;

        public FilterHeaderFn(String header) {
            this.header = header;
        }

        @ProcessElement
        public void processElement(ProcessContext c) {
            String row = c.element();

            if (!row.isEmpty() && !row.equals(this.header)) {
                c.output(row);
            }
        }
    }

    public static class ParseSchoolDataFn extends DoFn<String, School> {

        @ProcessElement
        public void processElement(@Element String line, OutputReceiver<School> out) {
            String[] data = line.split(",");

            School school = new School(Integer.parseInt(data[0]), data[1], data[2]);

            out.output(school);
        }
    }
}
