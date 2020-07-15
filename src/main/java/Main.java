import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.log4j.Logger;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;

public class Main {
	
	private static Logger _logger = Logger.getLogger(Main.class.getName());
	
	public static void main(String[] args){
		int num_stats = 1;
		int num_pstation = 2;
		int num_houses = 5;
		
		if(args.length>0) {
			num_pstation=Integer.parseInt(args[0].trim());
			if(args.length>1) {
				num_houses=Integer.parseInt(args[1].trim());
			}
		}
		
		LinkedHashMap<AgentType, Integer> init = new LinkedHashMap<AgentType, Integer>();
//		init.put(AgentType.City, 1);
		init.put(AgentType.Stats, num_stats);
		init.put(AgentType.PowerPlant, num_pstation);
		init.put(AgentType.House, num_houses);
//		init.put(AgentType.Appliance, 1);
//		init.put(AgentType.ElectricVehicle, 1);
		
		// Runtime is an singleton instance which represents the JADE runtime system
        Runtime rt = Runtime.instance();
        rt.setCloseVM(true);
        Profile profile = new ProfileImpl(null, -1, "SmartCity", true);
        AgentContainer mc = rt.createMainContainer(profile);
        
        try {
            AgentController rma = mc.createNewAgent("rma", "jade.tools.rma.rma", null);
            rma.start();
        } catch(StaleProxyException e) {
            e.printStackTrace();
        }
        
        for(Entry<AgentType, Integer> each: init.entrySet()) {
        	int count=each.getValue();
        	AgentType type = each.getKey();
        	for(int i=0; i<count; i++) {
        		try {
        			switch(type){
        			case PowerPlant:
        				if(i%2==0)//if even
        					mc.createNewAgent(type.toString()+"-"+i, type.toString(), new String[] {String.valueOf(20),String.valueOf(ThreadLocalRandom.current().nextInt(1, 2))}).start();
        				else //if odd
        					mc.createNewAgent(type.toString()+"-"+i, type.toString(), new String[] {String.valueOf(20),String.valueOf(ThreadLocalRandom.current().nextInt(2, 3))}).start();
        				break;
        			case House:
        				String house_name=type.toString()+"-"+i;
        				mc.createNewAgent(house_name, type.toString(), null).start();
        				try {
							Thread.sleep(2000);
						} catch (InterruptedException e1) {
							e1.printStackTrace();
						}
        				mc.createNewAgent(AgentType.Appliance.toString()+"-"+i+"-"+ApplianceTypes.airconditioner.toString(), AgentType.Appliance.toString(), new String[] {house_name,String.valueOf(3)}).start();
        				mc.createNewAgent(AgentType.Appliance.toString()+"-"+i+"-"+ApplianceTypes.car.toString(), AgentType.Appliance.toString(), new String[] {house_name,String.valueOf(2)}).start();
        				mc.createNewAgent(AgentType.Appliance.toString()+"-"+i+"-"+ApplianceTypes.lights.toString(), AgentType.Appliance.toString(), new String[] {house_name,String.valueOf(1)}).start();
        				break;
        			case Stats:
        				try {
							Thread.sleep(2000);
						} catch (InterruptedException e1) {
							e1.printStackTrace();
						}
        				mc.createNewAgent(type.toString()+"-"+i, type.toString(), null).start();
        				break;
					default:
						_logger.error("UNHANLDED");
						break;
        			}
        			
					
					try {
						Thread.sleep(3000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				} catch (StaleProxyException e) {
					_logger.error(ExceptionUtils.getMessage(e));
					_logger.error(ExceptionUtils.getStackTrace(e));
					System.exit(-1);
				}
        	}
        }
        
//        try {
//			Thread.sleep(15000);
//			mc.createNewAgent(AgentType.PowerPlant.toString()+"-"+30, AgentType.PowerPlant.toString(), new String[] {String.valueOf(7),"1"}).start();
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		} catch (StaleProxyException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
        
        
	}

}
