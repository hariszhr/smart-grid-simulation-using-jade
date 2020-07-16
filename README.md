# smart-grid-simulation-using-jade

This project uses a java farmework 'JADE' to create a network of agents, running in independent threads, communicating with each other to form a smart grid system.
Every entity producing/consuming electricity from power supply grid is part of the system.

Tha main source is the Power plants, who then supply power to houses.
Houses can choose to switch their subscription across Power stations based on which station is giving the best availabe rate.
Total consumption of houses is sum of all appliances, light, car, etc. inside the house.

The electric bill for each house is created on the fly, in real time.


# Run this simulation by:
  java -jar runner.jar


![Alt text](res/1.png?raw=true)


# User can add, remove or edit any agent in the system through JADE agent management system.
![Alt text](res/5.PNG?raw=true)

# to remove an agent:
![Alt text](res/4.PNG?raw=true)
