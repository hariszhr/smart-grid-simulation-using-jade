import java.util.ArrayList;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

@SuppressWarnings("serial")
public class PowerPlant extends Agent{
	
	private static Logger _logger = Logger.getLogger(PowerPlant.class.getName());
	
	public int _capacity = 0;
	
	public int _usage = 0;
	
	public int _price = 0;
	
	@Override
	protected void setup() {
		_capacity = Integer.parseInt((String) getArguments()[0]);
		_price = Integer.parseInt((String) getArguments()[1]);
		
//		Register in DF service
		try {
			Registration.register(getAID(), AgentType.PowerPlant.toString(), getLocalName(), this);
			Records.addPowerSupplier(getAID(), _capacity, _price);
			_logger.info("[AGENT CREATED] ["+getAID().getLocalName()+"] [Capacity: "+_capacity+" W] [Rate: "+_price+" $/Watt]");
			
		} catch (FIPAException e1) {
			e1.printStackTrace();
		}
		
//		reads incoming messages and accept or refuse requests - sends reply back
		addBehaviour(new INBOX(this));
		
//		monitor power usage - in case of excess power usage , unsubscribe houses
		addBehaviour(new OverUsageMonitoring(this, 3000));
	}
	
	public void takedown() {
		try {
			Registration.deregister(this);
			Records.removePowerSupplier(getAID());
			System.out.println(getAID().getName()+" terminating.");
		} catch (FIPAException e) {
			e.printStackTrace();
		}
		super.takeDown();
	}
	
//	private int checkOverUsage() {
//		float usage = _usage;
//		float capacity = _capacity;
//		float tmp = usage/capacity;
//		if(tmp>0.9){
//			int threshold = (int)(capacity*0.9);
//			return _usage-threshold;
//		}
//		return 0;
//	}
	
	public void set_price(int _price) {
		_logger.warn(getLocalName()+" price has been updated ["+_price+" $ per W]");
		this._price=_price;
	}
	
	public void set_capacity(int _capacity) {
		_logger.warn(getLocalName()+" capacity has been updated ["+_price+" W]");
		this._capacity=_capacity;
	}
	
	
	// process messages from outside
	private class INBOX extends CyclicBehaviour{
		private MessageTemplate msgTemplate=null;
		
		public INBOX(Agent a) {
			super(a);
			for(Enum<ConversationIDs> e: ConversationIDs.values()) {
				if(msgTemplate==null) {
					msgTemplate=MessageTemplate.MatchConversationId(e.name());
				} else {
					msgTemplate=MessageTemplate.or(msgTemplate, MessageTemplate.MatchConversationId(e.name()));
				}
			}
		}

		@Override
		public void action() {
			ACLMessage msg = myAgent.receive(msgTemplate);
			if(msg==null) {
				_logger.info("no msg recieved");
				block();
//				block(1000);
				return;
			} else {
				_logger.info("["+myAgent.getLocalName()+"] incoming request for "+msg.getConversationId()+" by "+msg.getSender().getName());
				_logger.debug("msg converstion id: "+msg.getConversationId());
				_logger.debug("msg content: "+msg.getContent());
				
				/*
				 * if(msg.getConversationId().equalsIgnoreCase(ConversationIDs.
				 * HOUSE__TELL_ME_RATE.toString())) { ACLMessage reply = msg.createReply();
				 * reply.setPerformative(ACLMessage.CFP);
				 * reply.setContent(String.valueOf(_price)); myAgent.send(reply);
				 * 
				 * } else
				 */ if(msg.getConversationId().equalsIgnoreCase(ConversationIDs.HOUSE__SUBSCRIPTION_REQUEST.toString())){
					
					ACLMessage reply = msg.createReply();
					reply.setPerformative(ACLMessage.CFP);
					
					int _already_subscribed_house_usage=0;
					if(Records.get_subscription_of_houses().get(msg.getSender())!=null &&  Records.get_subscription_of_houses().get(msg.getSender()).equals(getAID())) {
//						_logger.warn("["+msg.getSender().getLocalName()+"] ALREADY subscribed to this power supply ["+getLocalName()+"]");
//						reply.setContent(ConversationMessages.preapproved.toString());
						_already_subscribed_house_usage = Records.get_houses().get(msg.getSender());
						_logger.warn("XXX _already_subscribed_house_usage"+ _already_subscribed_house_usage);
					}
					
					/*
					 * if(_already_subscribed_house_usage>0) { consumption_of_subscribed_houses -=
					 * _already_subscribed_house_usage; }
					 */
					
					
					int capacity=_capacity;
					int consumption_of_subscribed_houses=0;
					
					ArrayList<AID> arr = new ArrayList<AID>();
					
					for(Entry<AID, AID> each : Records.get_subscription_of_houses().entrySet()) {
						if(each.getValue().getLocalName().equalsIgnoreCase(getAID().getLocalName())) {
							arr.add(each.getKey());
							consumption_of_subscribed_houses+=Records.get_houses().get(each.getKey());
						}
					}
					
					
					
					_logger.warn(getAID().getLocalName()+" [consumption_of_subscribed_houses] "+consumption_of_subscribed_houses+" [capacity] "+capacity);
					
					if(consumption_of_subscribed_houses < capacity) {
						reply.setContent(ConversationMessages.approved.toString());
					} else {
						_logger.info("["+msg.getSender().getLocalName()+"] REEEEFUSEEEEED by ["+getAID().getLocalName()+"]");
						reply.setContent(ConversationMessages.refused.toString());
					}
					
					
//					_usage -= _already_subscribed_house_usage;
//					if((_capacity - _usage) > Records.get_houses().get(msg.getSender())) {
//						reply.setContent(ConversationMessages.approved.toString());
//					} else {
//						_logger.info("["+msg.getSender().getLocalName()+"] REEEEFUSEEEEED by ["+getAID().getLocalName()+"]");
//						reply.setContent(ConversationMessages.refused.toString());
//					}
					
					myAgent.send(reply);
				}
				else {
					_logger.error("UNHANDLED CONVERSATION ID "+msg.getConversationId());
				}
				block();
			}
			
		}
		
	}
	
	
	
	private synchronized void check_over_usage() {
		

		int capacity=_capacity;
		int consumption_of_subscribed_houses=0;
		
		ArrayList<AID> to_be_forced_unsub = new ArrayList<AID>();
		
		for(Entry<AID, AID> each : Records.get_subscription_of_houses().entrySet()) {
			if(each.getValue().getLocalName().equalsIgnoreCase(getAID().getLocalName())) {
				to_be_forced_unsub.add(each.getKey());
				consumption_of_subscribed_houses+=Records.get_houses().get(each.getKey());
			}
		}
		
		_logger.warn(getAID().getLocalName()+" [consumption_of_subscribed_houses] "+consumption_of_subscribed_houses+" [capacity] "+capacity);
		
		Records.get_powerSuppliers_usage().put(getAID(), consumption_of_subscribed_houses);
		
		if(consumption_of_subscribed_houses>capacity) {
//		we need to remove sub of latest house	
			
			
//			bubble sort 'to_be_forced_unsub' according to subscription time 'ASC'
			int n=to_be_forced_unsub.size();
			for (int i = 0; i < n-1; i++) {
	            for (int j = 0; j < n-i-1; j++) {
	            	if (Records.get_subscription_timestamp().get(to_be_forced_unsub.get(j)) > Records.get_subscription_timestamp().get(to_be_forced_unsub.get(j+1))) 
		                { 
		                    // swap arr[j+1] and arr[i] 
		                    AID temp = to_be_forced_unsub.get(j);
		                    to_be_forced_unsub.add(j, to_be_forced_unsub.get(j+1));
		                    to_be_forced_unsub.add(j+1, temp);
		                } 
	            }					
			}
			
			
			int tb=0;
			for(int i=to_be_forced_unsub.size()-1; i>=0; i--) {
				if(tb<(consumption_of_subscribed_houses-capacity)) {
					tb+= Records.get_houses().get(to_be_forced_unsub.get(i));
				} else {
					to_be_forced_unsub.remove(i);
				}
			}
			
			
			for(AID each : to_be_forced_unsub) {
				_logger.warn("FORCED UNSUB OF ["+each.getLocalName()+"] from ["+getAID().getLocalName()+"]");
				
				Records.get_subscription_of_houses().remove(each);
				
//				send message: POWERPLANT__FORCE_UNSUBSCRIBE_HOUSE_ACTION
				ACLMessage cfp2 = new ACLMessage(ACLMessage.CFP);
				cfp2.addReceiver(each);
				cfp2.setConversationId(ConversationIDs.POWERPLANT__FORCE_UNSUBSCRIBE_HOUSE_ACTION.toString());
				send(cfp2);
			}

			
//			long target=0;
//			Collection<Long> c = Records.get_subscription_timestamp().values();
//			long[] longArray = ArrayUtils.toPrimitive(c.toArray(new Long[c.size()]));
//			Arrays.sort(longArray);
//			target = longArray[longArray.length-1];
//			
//			for(Entry<AID, Long> each : Records.get_subscription_timestamp().entrySet()) {
//				if(each.getValue()  == target) {
//					
//					
//					break;
//				}
//			}
			
		}
		
	
		
		
	} 
	
	private class OverUsageMonitoring extends TickerBehaviour{
		
		
		public OverUsageMonitoring(Agent a, long period) {
			super(a, period);
		}
		
		@Override
		protected void onTick() {
			
			check_over_usage();
			
		}
		
		
	}
	
	/*
	 * private class OverUsageMonitoringBehaviour extends TickerBehaviour{
	 * 
	 * ArrayList<String> tobeUnSubscribed = new ArrayList<>();
	 * 
	 * public OverUsageMonitoringBehaviour(Agent a, long period) { super(a, period);
	 * }
	 * 
	 * @Override protected void onTick() {
	 * 
	 * switch (_stateUnSubscribe) { case scanning: int excess=checkOverUsage();
	 * if(excess>0){ tobeUnSubscribed = new ArrayList<>(); ArrayList<String> keys =
	 * new ArrayList<>(_subscribers.keySet()); Collections.reverse(keys); for(String
	 * key: keys){ int usage=_subscribers.get(key); if(usage<=excess){
	 * tobeUnSubscribed.add(key); excess=excess-usage; } else { break; } }
	 * 
	 * if(tobeUnSubscribed.size()>0){ _stateUnSubscribe = States.unsubscribe; } }
	 * break; case unsubscribe: //TODO: send request to agent to unsubscribe
	 * 
	 * 
	 * 
	 * 
	 * _stateUnSubscribe=States.scanning; break; default: break; }
	 * 
	 * 
	 * 
	 * }
	 * 
	 * }
	 */
	
	
	

}
