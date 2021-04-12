Note:
	Due to problems with the regular garbage collector breaking the RMI connection of the peers, we opted to use a new 
	experimental No-Op Garbage Collector, Epsilon, to fix this problem, which means that Java 11 or above is required to run this application

Compile application:

	To compile the code, run, in the proj1/ directory the following command:
		./compile.sh


Run application:

	To run the application, go to the proj1/src/build/ directory and do the following steps:
		1.) Start RMI Registry, by running the following command:
			./rmi.sh
		2.) Initiate the desired number of peers, by running the following command as many times as required (#P = Peer number):
			./peer.sh 1.0 #P <peer_ap> 230.0.0.1 5001 230.0.0.2 5002 230.0.0.3 5003
		3.) Execute the commands:
			./test.sh <peer_ap> <sub_protocol> <opnd_1> <opnd_2>