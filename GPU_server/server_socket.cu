#include <stdio.h>
#include <stdlib.h>
#include "my_socket.h"
#include "init.cu"

void onAccept(int clientSocketFD){
  int numbytes;
  const int ACTION_MAX_DATA_SIZE = 20;
  char action[ACTION_MAX_DATA_SIZE];
  if ((numbytes = recv(
    clientSocketFD,
    action,
    ACTION_MAX_DATA_SIZE-1,
    0
  )) == -1) {
      perror("recv");
      exit(1);
  }
  action[numbytes] = '\0';
  printf("Action received: %s\n", action);
  if (strcmp(action, "INDEX") == 0){
    printf("Indexing...\n");
    int result = index_collection();
    const char *toSend = (result == 1)? "OK" : "NOK";
    if (send(clientSocketFD, toSend, 5, 0) == -1)
      perror("send indexing result");
  } else if (strcmp(action, "EVALUATE") == 0){
    printf("Evaluating...\n");
    const int QUERY_MAX_DATA_SIZE = 1000; // HARDCODED
    char query[QUERY_MAX_DATA_SIZE];
    if ((numbytes = recv(
      clientSocketFD,
      query,
      QUERY_MAX_DATA_SIZE-1,
      0
    )) == -1) {
        perror("recv");
        exit(1);
    }
    query[numbytes] = '\0';
    printf("Query: %s\n", query);
    resolveQuery(query);
  } else {
    printf("No action\n");
  }
  close(clientSocketFD);
}

void startServer(char* port){
  struct addrinfo *servinfo = getAddressInfo(port);
  struct addrinfo *p;
  int socketDescriptor;
  for(p = servinfo; p != NULL; p = p->ai_next) {
    socketDescriptor = getSocketDescriptor(p);
    if (socketDescriptor != -1){
      if (doBind(socketDescriptor, p) == -1){
        close(socketDescriptor);
      } else {
        break;
      }
    }
  }
  if (p == NULL)  {
      fprintf(stderr, "server: failed to bind\n");
      exit(1);
  }
  closeAddressInfo(servinfo);

  doListen(socketDescriptor);

  struct sigaction sa;
  sa.sa_handler = sigchld_handler; // reap all dead processes
  sigemptyset(&sa.sa_mask);
  sa.sa_flags = SA_RESTART;
  if (sigaction(SIGCHLD, &sa, NULL) == -1) {
      perror("sigaction");
      exit(1);
  }

  printf("server: waiting for connections...\n");

  struct sockaddr_storage their_addr; // connector's address information
  socklen_t sin_size;
  int clientSocketDescriptor;
  char s[INET6_ADDRSTRLEN];
  while(1) {  // main accept() loop
      sin_size = sizeof their_addr;
      clientSocketDescriptor = accept(socketDescriptor, (struct sockaddr *)&their_addr, &sin_size);
      if (clientSocketDescriptor == -1) {
          perror("accept");
          continue;
      }

      inet_ntop(their_addr.ss_family,
          get_in_addr((struct sockaddr *)&their_addr),
          s, sizeof s);
      printf("server: got connection from %s\n", s);

      //if (!fork()) { // this is the child process
          //close(socketDescriptor); // child doesn't need the listener
          onAccept(clientSocketDescriptor);
      //}
      close(clientSocketDescriptor);  // parent doesn't need this
  }
}

int main(int argc, char const *argv[]) {
  index_collection();
  char port[6];
  strcpy(port, "3491");
  startServer(port);
  return 0;
}
