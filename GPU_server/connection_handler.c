#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "connection_handler.h"
#include "gpu_handler.h"
#include "docscores.h"

// private functions
int readQueryLength(int socketfd);
char *readQueryString(int socketfd, int queryLength);
int sendEvaluationResponse(int socketfd, DocScores docScores);

void onIndexRequest(int socketfd){
  printf("Connection handler - Index Request\n");
  int result = loadIndexInGPUMemory();
  if (result == INDEX_LOADING_SUCCESS){
    result = INDEX_SUCCESS;
  } else {
    result = INDEX_FAIL;
  }
  result = htonl(result);
  if (
    send(
      socketfd,
      (char *)&result,
      sizeof(int),
      0)
      == -1)
    perror("send indexing result status");
}

void onQueryEvalRequest(int socketfd){
  printf("Connection handler - Eval Request\n");

  int qLength = readQueryLength(socketfd);
  char *query = readQueryString(socketfd, qLength);
  DocScores ds = evaluateQueryInGPU(query);
  sendEvaluationResponse(socketfd, ds);

  free(ds.scores);
  free(query);
}

int readQueryLength(int socketfd){
  // Reading length of query
  int numbytes, queryLength;
  numbytes = read_socket(
    socketfd,
    (char *)&queryLength,
    sizeof(int)
  );
  queryLength = ntohl(queryLength);
  printf("Query size: %d\n", queryLength);
  return queryLength;
}

char *readQueryString(int socketfd, int queryLength){
  int numbytes;
  char *query = malloc(queryLength + 1);;
  memset(query, 0, queryLength + 1);  //clear the variable
  if ((numbytes = read_socket(
    socketfd,
    query,
    queryLength
  )) == -1) {
      perror("recv");
      exit(1);
  }
  query[numbytes] = '\0';
  return query;
}

int sendEvaluationResponse(int socketfd, DocScores docScores){
  // first sends docs
  // (could be removed if the client knows number of docs in collection)
  int docs = htonl(docScores.size);
  if (send(
      socketfd,
      (char *)&docs,
      sizeof(int),
      0) == -1)
    perror("send docscores length");

  // Sending docScores to client
  int i, docId, weightStrLength;
  for (i=0; i < docScores.size; i++){
    printf("Sending doc %d: %.6f\n", i, docScores.scores[i]);

    // sending doc id
    //
    //could be removed if server always send every doc score
    // needed if gpu server decides to send only those docs
    // whose score exceeds some threshold (not currently the case)
    docId = htonl(i);
    if ( send(socketfd, (char *)&docId, sizeof(docId), 0) == -1) perror("send doc");

    // sending weight as string
    char weightStr[10];
    snprintf(weightStr, 10, "%.4f", docScores.scores[i]);
    weightStrLength = htonl(strlen(weightStr));
    if ( send(socketfd, (char *)&(weightStrLength), sizeof(int), 0) == -1) perror("send doc");
    if ( send(socketfd, weightStr, strlen(weightStr), 0) == -1) perror("send doc");
  }

  return 0;
}