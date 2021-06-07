import time
import json
import threading
import pandas as pd
from kafka import KafkaProducer, KafkaConsumer,TopicPartition

class StreamProducer:
    """
    Python producer for Kafka.

    ...

    Attributes
    ----------
    producer : KafkaProducer
        a Kafka Producer component connected to the cluster
    df : pd.DataFrame
        in-memory dataframe with all data available (+3M rows)
    topic: str
        topic where information should be published

    Methods
    -------
    run()
        Read dataframe row by row and send the data to Kafka (no key used)
    """
    
    def __init__(self,topic:str ) -> None:
        # Read the data from the csv and store them in memory
        self.df: pd.DataFrame = pd.read_csv("data/stats.csv").sort_values('second')

        # Create the producer using the dns name given by Docker in docker-compose.yaml
        self.producer: KafkaProducer = KafkaProducer(bootstrap_servers='kafka:9092')

        self.topic: str = topic
        
        
        #print(self.df.describe(include='all'))
    

    def run(self) -> None:
        for row in self.df.itertuples():

            line = {'timestamp': row.second, 'views':  row.device_count, 'channel': row.channel_id }
            key = str(row.channel_id)#+row.second
            # message value and key must be raw bytes
            jd = json.dumps(line).encode('utf-8')

            # send to topic on broker
            self.producer.send(self.topic, value=jd, key=key.encode('utf-8'))

            #print("SENT: ",line)

            # wait half second
            time.sleep(1/5)

        print('PRODUCER COMPLETED ITS JOB')



class StreamConsumer:
    """
    Python consumer for Kafka. Used for debug purpuse now
    TODO: Extend with monitoring capabilities

    ...

    Attributes
    ----------
    consumer : KafkaConsumer
        a Kafka Consumer component connected to the cluster
    topic: str
        topic from which information should be collected

    Methods
    -------
    run()
        Extract data from Kafka cluster and print one message every 1000
    """

    def __init__(self, topic: str) -> None:
        # give broker IP from docker
        self.consumer = KafkaConsumer(bootstrap_servers='kafka:9092')
        self.topic: str = topic
        self.consumer.subscribe(self.topic)

    def run(self) -> None:
        idx: int = 0
        # consumer.seek_to_beginning(topic_partition)
        for message in self.consumer:
            if idx == 500:
                print("%s key=%s value=%s" % (message.topic, message.key, message.value))
                print(self.consumer.metrics())
                idx = 0
            idx += 1

        # inform cluster how much we have read
        self.consumer.commit()

        print('CONSUMER FETCHED ALL DATA AVAILABLE')



if __name__ == "__main__":
    """ 
    Creates the entities and start running both consumer and producer in specific threads.
    TODO: Create one producer thread for each channel_id
    """


    print('PYTHON IS RUNNING')
    topic: str = "views_flow"
    producer: StreamProducer = StreamProducer(topic)
    consumer: StreamConsumer = StreamConsumer(topic)
    # Create two threads as follows
    try:
        t1 = threading.Thread(target=producer.run, name="Thread-Produce" )
        t2= threading.Thread(target=consumer.run, name="Thread-Consume" )
        t1.start()
        t2.start()
    except:
        print("Error: unable to start thread")
    print("BOTH THREAD STARTED")
    t1.join()
    t2.join()