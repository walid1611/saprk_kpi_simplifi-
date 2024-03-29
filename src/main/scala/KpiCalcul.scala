import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions._
import org.apache.spark.sql._
import org.elasticsearch.spark._

object KpiCalcul {


  def main(args: Array[String]): Unit = {
 val input = args(0)  //"hdfs:///demo/data/aapl-2017.csv"
 val output = args(1) ///"hdfs:///demo/data/test/test2"

    val spark = SparkSession.builder//.master("local[*]")
                            .appName(s"OneVsRestExample")
                            .config("spark.es.nodes","127.0.0.1")
                            .config("spark.es.port","9200")
                            .config("es.index.auto.create", "true")
                            .getOrCreate()


    spark.sparkContext.setLogLevel("WARN")

     val appleDF = spark.read.format("csv").
      option("header", "true").
      option("inferSchema", "true").load(input)
      //.load("/home/walid/data/aapl-2017.csv")


    import spark.implicits._
    val mrcfit_Sans_Previous_kpis = appleDF.withColumn("DATE_ACTION",trunc(col("Date"), "mm")) //je tranc la date (mois)
      .withColumn("ID_STRUCTURE", concat(lit("User"), dayofmonth(col("Date")).cast("String"))) //j'ajoute la clé(id user)
     // .withColumn("CD_POSTE_TYPE", concat(lit("Name"), dayofyear(col("Date")).cast("String"))) //j'ajoute la clé(id utilsation)
      .withColumnRenamed("Volume", "IND_NB_USER_DST")
      .select("DATE_ACTION", "ID_STRUCTURE", "IND_NB_USER_DST", "Low", "High")


      ///les kpis
      .withColumn("var_log_High", abs(log(col("High"))))
      .withColumn("var_exp_High", abs(exp(sin(col("High")))))
      .withColumn("var_cos_High", abs(cos(col("High"))))
      .withColumn("var_sin_High", abs(sin(col("High"))))
      .withColumn("var_log_Low", abs(log(col("Low"))))
      .withColumn("var_exp_Low", abs(exp(sin(col("Low")))))
      .withColumn("var_cos_Low", abs(cos(col("Low"))))
      .withColumn("var_sin_Low", abs(sin(col("Low"))))
      .withColumn("var_sin_Low", abs(sin(col("Low"))))
      .filter($"ID_STRUCTURE".like("User20"))

      .orderBy("DATE_ACTION")


   mrcfit_Sans_Previous_kpis.show(50)


    def duplic_copute_prev(df: DataFrame, pa: Int): DataFrame = {
      import org.apache.spark.sql.functions._
      import spark.implicits._

      df.createOrReplaceTempView("vu")

      val maxDATE_ACTION = "'" + spark.sql("select max(DATE_ACTION) from vu ").collect().map(u => u(0)).toList.head + "'"

      val df1 = df.withColumn("DATE_ACTION", add_months(col("DATE_ACTION"), pa))
        .filter("DATE_ACTION <= " + maxDATE_ACTION)
        .withColumn("IND_NB_USER_DST", lit(0))
        .withColumn("Low", lit(0))
        .withColumn("High", lit(0))
        .withColumn("var_log_High", lit(0))
        .withColumn("var_exp_High", lit(0))
        .withColumn("var_cos_High", lit(0))
        .withColumn("var_sin_High", lit(0))
        .withColumn("var_log_Low",  lit(0))
        .withColumn("var_exp_Low",  lit(0))
        .withColumn("var_cos_Low",  lit(0))
        .withColumn("var_sin_Low",  lit(0))
      val df3 = df.union(df1)
      val df4 = df3.dropDuplicates("DATE_ACTION", "ID_STRUCTURE")
      df4
    }




    def duplic_bddf(df: DataFrame): DataFrame = {
      import spark.implicits._
      val dfbddf = duplic_copute_prev(df, 5).
        union(duplic_copute_prev(df, 4)).
        union(duplic_copute_prev(df, 3)).
        union(duplic_copute_prev(df, 2)).
        union(duplic_copute_prev(df, 1))
        .dropDuplicates("DATE_ACTION", "ID_STRUCTURE")
      dfbddf
    }



    val joincle = Seq("DATE_ACTION", "ID_STRUCTURE")

    val base_kpis = Seq("IND_NB_USER_DST", "Low", "High", "var_log_High", "var_exp_High", "var_cos_High", "var_sin_High",
      "var_log_Low", "var_exp_Low", "var_cos_Low", "var_sin_Low")

    val nomkpis_preced1 = Seq("IND_NB_USER_DST_PM1", "Low_PM1", "High_PM1", "var_log_High_PM1", "var_exp_High_PM1",
      "var_cos_High_PM1", "var_sin_High_PM1", "var_log_Low_PM1", "var_exp_Low_PM1", "var_cos_Low_PM1", "var_sin_Low_PM1")

    val name_all_kpis = joincle
      .union(base_kpis)
      .union(nomkpis_preced1)
      .union(nomkpis_preced1.map(x => x.replace("1", "2")))
      .union(nomkpis_preced1.map(x => x.replace("1", "3")))
      .union(nomkpis_preced1.map(x => x.replace("1", "4")))
      .union(nomkpis_preced1.map(x => x.replace("1", "5")))


    //je dupliquqe

    val v0 = duplic_bddf(mrcfit_Sans_Previous_kpis)


    //j'incremente les date sur des different dataframe
    val v1 = v0.withColumn("DATE_ACTION", add_months(col("DATE_ACTION"), 1))
    val v3 = v0.withColumn("DATE_ACTION", add_months(col("DATE_ACTION"), 2))
    val v4 = v0.withColumn("DATE_ACTION", add_months(col("DATE_ACTION"), 3))
    val v6 = v0.withColumn("DATE_ACTION", add_months(col("DATE_ACTION"), 4))
    val v12 =v0.withColumn("DATE_ACTION", add_months(col("DATE_ACTION"), 5))




    //jointure des kpis

    val vf = v0.join(v1, joincle, joinType = "left")
      .join(v3, joincle, joinType = "left")
      .join(v4, joincle, joinType = "left")
      .join(v6, joincle, joinType = "left")
      .join(v12, joincle, joinType = "left").toDF(name_all_kpis: _*).na.fill(0)
      .filter($"ID_STRUCTURE".like("User20"))
        .orderBy("DATE_ACTION")

vf.coalesce(1)
    .write
    .format("csv")
    .save(output)


   // import org.elasticsearch.spark.sql._

   //vf.saveToEs("spark3/mappings")


   //vf.show(100)





  }

}
