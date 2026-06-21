FROM docker.elastic.co/elasticsearch/elasticsearch:8.12.2

RUN bin/elasticsearch-plugin install -b https://get.infini.cloud/elasticsearch/analysis-ik/8.12.2 \
    && bin/elasticsearch-plugin install -b https://get.infini.cloud/elasticsearch/analysis-pinyin/8.12.2
