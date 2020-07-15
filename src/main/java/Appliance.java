import org.apache.log4j.Logger;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class Appliance extends Agent{
	
	private static final long serialVersionUID = -5199376818524797104L;
	private volatile int _consumption_Wh=0;
	private volatile int _storage_capacity=0;
	private volatile int _max_storage_capacity=20;
	private AID _house_aid;
	private int _billing_hour_interval=5000;
	private static Logger _logger = Logger.getLogger(Appliance.class);
	private volatile int _step=1;
//	private volatile int _hour=0;
//	private volatile int _day=1;
	
	@Override
	protected void setup() {
		try {
			String house_name=(String) getArguments()[0];
			
			boolean found=false;
			for(AID house: Records.get_houses().keySet()) {
				if(house.getLocalName().equalsIgnoreCase(house_name)) {
					found=true;
					break;
				}
			}
			if(!found) {
				takeDown();
			}
			
			_consumption_Wh=Integer.parseInt((String) getArguments()[1]);
			if(getArguments().length > 2) {
				_storage_capacity=Integer.parseInt((String) getArguments()[2]);
			}
			Registration.register(getAID(), AgentType.Appliance.toString(), getLocalName(), this);
			
			Records.addAppliance(getAID(), house_name, _consumption_Wh, _storage_capacity);
			
			_house_aid = Records.get_appliance_to_house_mapping().get(getAID());
			
			_logger.info("[AGENT CREATED] ["+getAID().getLocalName()+"] [Consumption] "+_consumption_Wh+" Wh] [Storage] ["+_storage_capacity+" W]");
			
			addBehaviour(new ConsumptionTickBehaviour(this, _billing_hour_interval));
			addBehaviour(new INBOX(this));
			
		} catch (FIPAException e1) {
			e1.printStackTrace();
			takeDown();
		}
		
		
//		add behaviours
		
	}
	
	@Override
	protected void takeDown() {
		try {
			Registration.deregister(this);
			Records.removeAppliance(getAID());
			
			ACLMessage msg = new ACLMessage(ACLMessage.CFP);
			msg.addReceiver(_house_aid);
			msg.setConversationId(ConversationIDs.DELETE_APPLIANCE.toString());
			this.send(msg);
			
			System.out.println(getAID().getName()+" terminating.");
		} catch (FIPAException e) {
			e.printStackTrace();
		}
		super.takeDown();
	}
	
	
	private class ConsumptionTickBehaviour extends TickerBehaviour {

		private static final long serialVersionUID = -3033614105299991652L;
		
		
		public ConsumptionTickBehaviour(Agent a, long period) {
			super(a, period);
		}
		
		@Override
		protected void onTick() {
			
			switch (_step) {
			case 1:
				_logger.info(ConversationIDs.ADD_APPLIANCE.toString()+ " ["+myAgent.getLocalName()+"]");
				ACLMessage msg = new ACLMessage(ACLMessage.CFP);
				msg.addReceiver(_house_aid);
				msg.setConversationId(ConversationIDs.ADD_APPLIANCE.toString());
				myAgent.send(msg);
				_step=2;
				break;
			case 2:
//				if(_hour==24) {
//					_day++;
//					_hour=0;
//				}
				
				_logger.info(ConversationIDs.APPLIANCE_CONSUMPTION_TICK.toString()+ " ["+myAgent.getLocalName()+"] [DAY "+Records.get_day().get()+"] [HOUR "+Records.get_hour().get()+"]");
				ACLMessage msg2 = new ACLMessage(ACLMessage.CFP);
				msg2.addReceiver(_house_aid);
				msg2.setConversationId(ConversationIDs.APPLIANCE_CONSUMPTION_TICK.toString());
				
				String tmp = getLocalName().split("-")[getLocalName().split("-").length-1];
				
				if( tmp.equalsIgnoreCase(ApplianceTypes.airconditioner.toString())) {
					if(ApplianceSchedule.airconditioner[Records.get_hour().get()]!=-1) {
						msg2.setContent(String.valueOf(_consumption_Wh));
					} else {
						msg2.setContent(String.valueOf(-1));
					}
				} else if( tmp.equalsIgnoreCase(ApplianceTypes.lights.toString())) {
					if(ApplianceSchedule.lights[Records.get_hour().get()]!=-1) {
						msg2.setContent(String.valueOf(_consumption_Wh));
					} else {
						msg2.setContent(String.valueOf(-1));
					}
				} else if( tmp.equalsIgnoreCase(ApplianceTypes.car.toString())) {
					
					if(ApplianceSchedule.car[Records.get_hour().get()]!=-1) {
						if(Records.houseHasSubscription(_house_aid)){
							if(Records.get_appliances_capacity().get(getAID())<_max_storage_capacity){
								int ii=(Records.get_appliances_capacity().get(getAID())+_consumption_Wh);
								Records.get_appliances_capacity().put(getAID(), ii);
								
								if(Records.get_appliances_capacity().get(getAID())>_max_storage_capacity){
									
									Records.get_appliances_capacity().put(getAID(), _max_storage_capacity);
								}
								
								msg2.setContent(String.valueOf(_consumption_Wh));	
							} else {
								msg2.setContent(String.valueOf(-1));
							}
//							Records.get_appliances_capacity().put(getAID(), _storage_capacity);
						}
						
						
					} else {
						Records.get_appliances_capacity().put(getAID(), 0);
						msg2.setContent(String.valueOf(-1));
					}
				}
				
				
				_logger.warn("testing code below...");
				if(msg2.getContent()==null || msg2.getContent().isEmpty()) {
					msg2.setContent("-1");
				}
				
				
				myAgent.send(msg2);
//				_hour++;
//				if(_hour==25) {
//					_day++;
//				}
				break;
			default:
				break;
			}
			
			
		}
		
	}
	
	
	
	
	private class INBOX extends CyclicBehaviour {
		
		private static final long serialVersionUID = -7768862992249841220L;
		private MessageTemplate msgTemplate=MessageTemplate.MatchConversationId(ExtConversationIDs.EXT_UPDATE_APPLIANCE_CONSUMPTION.toString());
		
		public INBOX(Agent a) {
			super(a);
		}

		@Override
		public void action() {
			ACLMessage msg = myAgent.receive(msgTemplate);
			if(msg==null) {
//				nothing
			} else {
				_logger.debug("msg converstion id: "+msg.getConversationId());
				_logger.debug("msg content: "+msg.getContent());
				
				try {
					switch (ExtConversationIDs.valueOf(msg.getConversationId())) {
					
					case EXT_UPDATE_APPLIANCE_CONSUMPTION:
						_consumption_Wh = Integer.valueOf(msg.getContent().trim());
						Records.get_appliances_consumption().put(getAID(), _consumption_Wh);
						break;
					default:
						break;
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				
			}
			block();
		}
		
	}
	
	
	
	
	
	

}
