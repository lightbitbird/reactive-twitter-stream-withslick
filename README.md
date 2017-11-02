# Reactive Twitter Stream With Slick

#

**Description**  
Before running this app, you need your access token for a twitter streaming and SQS access key.  

1. Akka actor gets tweets from Twitter Streaming API and chunk them into words, register them in DB.
   A prefecture number is enable to change on TwitterBatchActor.

![](https://user-images.githubusercontent.com/14951865/32171589-ff5739ee-bdbb-11e7-98c9-7bdff03ae087.JPG)

2. Akka actor sends SQS Message queues using Akka Actor daemon as a background process.
SQS ids are managed by other actor on database, the actor collects words each by prefecture in the score table with Akka Streams.
These process are not efficient, some queries and the score table are not necessary, just for testing SQS, Slick, Akka streams, actor daemon.  
https://github.com/lightbitbird/twitter-words-map

![](https://user-images.githubusercontent.com/14951865/32171592-002a091e-bdbc-11e7-8115-e9397ae110a4.JPG)

3. Registerd words are pointed at each by prefecture on Google Map. You can see them on the angular frontend page.

![](https://user-images.githubusercontent.com/14951865/32171595-01f6d1e6-bdbc-11e7-8daa-f5679d8b35f2.JPG)

#
**Usage**  

Here Multiple main classes, select them to run.  
Execute `sbt run` in order to run the Main application.

 [1] com.twitter.TwitterStreamer

     Akka actor gets tweets from Twitter Streaming API and chunk them into words, register them in DB.
     You can change a prefecture number on TwitterBatchActor.

 [2] com.resident.MessageHandler

     Akka actor sends SQS Message queues using Akka Actor daemon as a background process.

 [3] com.restapi.TwitterWordsApplication

     Registerd words are pointed at by a prefecture on Google Map.
  

#
**The Frontend Google Map Application**  
You can get the frontend application from the following repository  
https://github.com/lightbitbird/twitter-words-map
