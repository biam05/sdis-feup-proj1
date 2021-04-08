echo "--------- SDIS T1G06 ---------"

echo "--------- Starting RMI Registry ---------"
start rmiregistry -J-Djava.class.path=src/out/production/proj1

echo "--------- Starting Peer 1 ---------"
start java -Djava.rmi.server.codebase=src/out/production/proj1/sdis/t1g06/ -Djava.security.manager -Djava.security.policy=src/rmipolicy/my.policy/ -classpath src/out/production/proj1 sdis.t1g06.Peer 1.0 1 ServiceInterface 224.0.0.1 4441 224.0.0.2 4442 224.0.0.3 4443

echo "--------- Starting Peer 2 ---------"
start java -Djava.rmi.server.codebase=src/out/production/proj1/sdis/t1g06/ -Djava.security.manager -Djava.security.policy=src/rmipolicy/my.policy/ -classpath src/out/production/proj1 sdis.t1g06.Peer 1.0 2 ServiceInterface 224.0.0.1 4441 224.0.0.2 4442 224.0.0.3 4443

echo "--------- Starting Peer 3 ---------"
start java -Djava.rmi.server.codebase=src/out/production/proj1/sdis/t1g06/ -Djava.security.manager -Djava.security.policy=src/rmipolicy/my.policy/ -classpath src/out/production/proj1 sdis.t1g06.Peer 1.0 3 ServiceInterface 224.0.0.1 4441 224.0.0.2 4442 224.0.0.3 4443

echo "--------- Starting Peer 4 ---------"
start java -Djava.rmi.server.codebase=src/out/production/proj1/sdis/t1g06/ -Djava.security.manager -Djava.security.policy=src/rmipolicy/my.policy/ -classpath src/out/production/proj1 sdis.t1g06.Peer 1.0 4 ServiceInterface 224.0.0.1 4441 224.0.0.2 4442 224.0.0.3 4443

echo "--------- Starting Peer 5 ---------"
start java -Djava.rmi.server.codebase=src/out/production/proj1/sdis/t1g06/ -Djava.security.manager -Djava.security.policy=src/rmipolicy/my.policy/ -classpath src/out/production/proj1 sdis.t1g06.Peer 1.0 5 ServiceInterface 224.0.0.1 4441 224.0.0.2 4442 224.0.0.3 4443

sleep 0.5

echo "--------- Starting testApp ---------"
start java  -classpath src/out/production/proj1 sdis.t1g06.TestApp 1 BACKUP circle.jpg 1
#start java  -classpath src/out/production/proj1 sdis.t1g06.TestApp 1 RESTORE circle.jpg