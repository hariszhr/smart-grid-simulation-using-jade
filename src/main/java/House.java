import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class House extends Agent{
	
	private static final long serialVersionUID = -4530682728333683915L;

	private static Logger _logger = Logger.getLogger(House.class);
	
	private volatile int _consumption= 0;
	
//	INFO about subscription
	private volatile int _subscription_attempts=0; //after 5 attempts. try to subscribe to next best supplier. or if nothing, keeping trying in same one.
	private volatile long _current_subscription_time;
	private volatile AID _current_subscription_AID;
	private volatile ArrayList<AID> _i_m_blocked_from_list = new ArrayList<AID>();
	private volatile int _i_m_blocked_from_list_count=0;
	
	private AtomicInteger _bill = new AtomicInteger(0);
	
	private volatile ArrayList<AID> _localSuppliersList = new ArrayList<AID>();
	private volatile ArrayList<AID> _appliances = new ArrayList<AID>();
	
	
	
	@Override
	protected void setup() {
		try {
//			_consumption=Integer.parseInt((String) getArguments()[0]);
			
			Registration.register(getAID(), AgentType.House.toString(), getLocalName(), this);
			Records.addHouse(getAID(), _consumption);
			
			_logger.info("[AGENT CREATED] ["+getAID().getLocalName()+"] [Consumption Rate: "+_consumption+" Wh]");
		} catch (FIPAException e1) {
			e1.printStackTrace();
		}
		
		addBehaviour(new SubcriptionSmartMeterBehaviour(this, 5000));
		addBehaviour(new BillingSmartMeterBehaviour(this, 5000));
		addBehaviour(new INBOX(this));
	}
	
	@Override
	protected void takeDown() {
		try {
			Registration.deregister(this);
			Records.removeHouse(getAID());
			System.out.println(getAID().getName()+" terminating.");
		} catch (FIPAException e) {
			e.printStackTrace();
		}
		super.takeDown();
	}
	
	private int get_current_house_consumption_rate() {
		int rslt=0;
		for(AID each : _appliances) {
			rslt+=Records.get_appliances_consumption().get(each);
		}
		return rslt;
	}
	
	private class BillingSmartMeterBehaviour extends TickerBehaviour {
		
		private static final long serialVersionUID = -1702632007378105030L;

		public BillingSmartMeterBehaviour(Agent a, long period) {
			super(a, period);
		}

		@Override
		public void onTick() {
			
			int s_power=0;
			for(AID ee :_appliances){
				s_power += Records.get_appliances_capacity().get(ee);
			}
			
			if(Records.get_subscription_of_houses().containsKey(getAID())) {
				int consumption = _consumption;
				int rate = Records.get_powerSuppliers_rate().get(Records.get_subscription_of_houses().get(getAID()));
				_bill.addAndGet(consumption*rate);
				Records.get_house_bills().put(getAID(), _bill.get());
				_logger.info("["+getLocalName()+"] [BILL] ["+_bill+"] last hour increment ["+consumption+" * "+rate+"]");
				_consumption=0;
			} else if (s_power>_consumption){
				Records.get_houses_on_electric_car_power().put(getAID(), s_power);
				s_power-=_consumption;
			} else {
				Records.get_houses_on_electric_car_power().remove(getAID());
			}
		}
		
	}
	
//	#################################### INCOMING MSGS ###################################################
	private class INBOX extends CyclicBehaviour {
		
		private static final long serialVersionUID = -8763792571169028380L;
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
				block();
				return;
			} else {
				_logger.debug("msg converstion id: "+msg.getConversationId());
				_logger.debug("msg content: "+msg.getContent());
				
				
				switch (ConversationIDs.valueOf(msg.getConversationId())) {
				
				case HOUSE__SUBSCRIPTION_REQUEST:
					
					if(_state == SmartMeterStates.subscribe || _state == SmartMeterStates.updatesubscription) {
						_subscription_attempts++;
						String message = msg.getContent();
						
						if(message.equalsIgnoreCase(ConversationMessages.approved.toString())) {
							_current_subscription_time = Records.getCurrentTime();
							_current_subscription_AID = msg.getSender();
							Records.registerHouseSubscription(getAID(), msg.getSender(), _current_subscription_time);
							
							_logger.info("[[SUBSCRIPTION]] ["+getLocalName()+"] TO ["+msg.getSender().getLocalName()+"] ON TIME ["+_current_subscription_time+" seconds]");
							_logger.info("Subscription Attempt: ["+_subscription_attempts+"]");
							
							_subscription_attempts=0;
							_state = SmartMeterStates.scanning;
							
							Records.get_houses_on_electric_car_power().remove(getAID());
							
						} else if(message.equalsIgnoreCase(ConversationMessages.preapproved.toString())) {
							_subscription_attempts=0;
							_state = SmartMeterStates.scanning;
							Records.get_houses_on_electric_car_power().remove(getAID());
//							maybe not needed...
						} else if(message.equalsIgnoreCase(ConversationMessages.refused.toString())) {
							_subscription_attempts=0;
							_state = SmartMeterStates.scanning;
						} 
					}
					break;
				case POWERPLANT__FORCE_UNSUBSCRIBE_HOUSE_ACTION:
					_current_subscription_AID=null;
					_current_subscription_time=0;
					if(!_i_m_blocked_from_list.contains(msg.getSender())) {
						_i_m_blocked_from_list.add(msg.getSender());
					}
					_i_m_blocked_from_list_count=0;
					_state = SmartMeterStates.scanning;
					break;
				
				case APPLIANCE_CONSUMPTION_TICK:
					if(Records.houseHasSubscription(getAID())) {
						if(Records.getCurrentTime()-Records.get_subscription_timestamp().get(getAID()) > 5) {
							
							int tick_value;
							
							_logger.info("["+getAID()+"] [message] "+msg.getContent());
							
							tick_value = Integer.parseInt(msg.getContent());
							
							if(tick_value == -1) {
								break;
							}
							_consumption+=tick_value;
							_logger.info("["+msg.getSender().getLocalName()+"] "+ConversationIDs.APPLIANCE_CONSUMPTION_TICK.toString()+" - "+_consumption + "Wh");
						} else {
							_logger.info("...");
						}
					}
					
					break;
				case ADD_APPLIANCE:
					AID appliance_aid_to_add = msg.getSender();
					_appliances.add(appliance_aid_to_add);
					
					_logger.info("["+getAID().getLocalName()+"] has registered an appliance ["+msg.getSender().getLocalName()+"]");
					break;
				case DELETE_APPLIANCE:
					AID appliance_aid_to_delete = msg.getSender();
					_appliances.remove(appliance_aid_to_delete);
					break;
				default:
					break;
				}
				
				block();
			}
		}
		
	}
//	#################################### INCOMING MSGS - END ###################################################
	
	
//	******************* SMART METER ***************************************************
	private enum SmartMeterStates {updateconsumption, subscribe, unsubscribe, updatesubscription, scanning}
	private SmartMeterStates _state = SmartMeterStates.scanning;
	
	private class SubcriptionSmartMeterBehaviour extends TickerBehaviour {
		
		private static final long serialVersionUID = 3560009272314777761L;
		private AID new_target_supplier = null;

		public SubcriptionSmartMeterBehaviour(Agent a, long period) {
			super(a, period);
		}
		
		@Override
		protected void onTick() {
			_logger.info("["+getLocalName()+"] [Smart Meter] is in ["+_state.toString()+"] mode");
			
			switch (_state) {
			
			case scanning:
				
				new_target_supplier=null;
				int current_consumption = get_current_house_consumption_rate();
				if(current_consumption!=Records.get_houses().get(getAID())) {
					Records.get_houses().put(getAID(), current_consumption);
				}
				
				
				
//				add any new power supply
				for(AID each: Records.get_powerSuppliers_capacity().keySet()) {
					if(_localSuppliersList.indexOf(each) == -1) {
						_localSuppliersList.add(each);
					}
				}
				
//				Remove any unavailable power supply
				for(int i=0; i< _localSuppliersList.size(); i++ ) {
					if(!Records.get_powerSuppliers_capacity().containsKey(_localSuppliersList.get(i))) {
						_localSuppliersList.remove(i);
					}
				}
				
				
				_logger.debug("i_m_blocked_from_list_count "+_i_m_blocked_from_list_count);
				_i_m_blocked_from_list_count++;
				if(_i_m_blocked_from_list_count>23) {
					_i_m_blocked_from_list.clear();
				}
				
				_logger.debug("****************** block list ***********************************");
				for(AID e : _i_m_blocked_from_list) {
					_logger.debug(e.getLocalName());
				}
				_logger.debug("****************** end - block list ***********************************");
				
				
//				create available_list by removing blocked suppliers
				ArrayList<AID> available_suppliers = new ArrayList<AID>(); 
				for(AID each : _localSuppliersList) {
					if(!_i_m_blocked_from_list.contains(each)) {
						available_suppliers.add(each);
					}
				}
				
				
				_logger.debug("****************** available list ***********************************");
				for(AID e : available_suppliers) {
					_logger.debug(e.getLocalName());
				}
				_logger.debug("****************** end - available list ***********************************");
				
//				************ If no subscription - then change state to subscribe **********************
				if(!Records.houseHasSubscription(getAID()) && available_suppliers.size()>0) {
					new_target_supplier = findBestAID(available_suppliers);
					if(new_target_supplier!=null) _state=SmartMeterStates.subscribe;
					break;
//				*********** END - If no subscription - then change state to subscribe *****************
					
//				*********** FIND BETTER SUPPLIER RATE ...	
				} else if(Records.houseHasSubscription(getAID()) && (Records.getCurrentTime()-_current_subscription_time) > 10 ) {
					AID a = findBestAID(available_suppliers);
					if(a != null && !a.equals(_current_subscription_AID)) {
						
//						only try subscribing if power station has capacity for it
						if((Records.get_powerSuppliers_capacity().get(a)-Records.get_powerSuppliers_usage().get(a)) >= Records.get_houses().get(getAID())) {
							new_target_supplier = a;
							_state = SmartMeterStates.updatesubscription;
						}
						
					}
				}
//				
////				************ Search for new/update Suppliers
//				try {
//					DFAgentDescription template = Registration.DFDTemplate(AgentType.PowerPlant.toString());
//					DFAgentDescription[] dfa =  DFService.search(myAgent, template);
//					if(dfa.length>0) {
//						
////						************ sending rate request to power suppliers ****************************
//						ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
//						ArrayList<AID> tmp = new ArrayList<AID>();
//						for(DFAgentDescription each: dfa) {
//							tmp.add(each.getName());
//							cfp.addReceiver(each.getName());
//						}
//						
//						for(AID each: _localSuppliersList) {
//							if(!tmp.contains(each)) {
//								_logger.warn("["+each.getName()+"] is no longer available. Removing it from Records");
//								Records.removePowerSupplier(each);
//								_localSuppliersList.remove(each);
//							}
//						}
//						
//						cfp.setConversationId(ConversationIDs.HOUSE__TELL_ME_RATE.toString());
//						cfp.setContent(getName()+" [smart meter] sending requests for prices.. - "+System.currentTimeMillis());
//						myAgent.send(cfp);
////						************ END - sending rate request to power suppliers ****************************
//						
//					} else {
//						_logger.debug("No "+AgentType.PowerPlant.toString()+" found");
//					}
//				} catch (FIPAException e) {
//					e.printStackTrace();
//				}
				
				
				break;
			case subscribe:
				ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
				cfp.addReceiver(new_target_supplier);
				cfp.setConversationId(ConversationIDs.HOUSE__SUBSCRIPTION_REQUEST.toString());
				myAgent.send(cfp);
				break;
			case updatesubscription:
				//TODO: if current supplier has better rate, then we update subscription only
				ACLMessage cfp2 = new ACLMessage(ACLMessage.CFP);
				cfp2.addReceiver(new_target_supplier);
				cfp2.setConversationId(ConversationIDs.HOUSE__SUBSCRIPTION_REQUEST.toString());
				myAgent.send(cfp2);
				
				break;
			case unsubscribe:
				
				//TODO: unsubscribe current subscription . send msg to supplier to unsubscribe
				
				break;
			default:
				_logger.error("UNHANLDED");
				break;
			}
		
		}
		
		private AID findBestAID(ArrayList<AID> suppliersList) {
			
			if(suppliersList.size()==0) return null;
			
			
			if(_current_subscription_AID !=null) {
				_logger.error("REVIEW THISSSSSSSSSSSSSSSSSSSSSSSSSS if");
				
				int current_rate=Records.get_powerSuppliers_rate().get(_current_subscription_AID);
				int best = suppliersList.indexOf(_current_subscription_AID);
				if(best==-1) best=0; //since this code should run when suupliers list > 0
				
				_logger.debug("---------------------------------");
				_logger.debug(current_rate);
				_logger.debug(best);
				
				for(int i=0; i<suppliersList.size(); i++) {
					int rate = Records.get_powerSuppliers_rate().get(suppliersList.get(i));
					if(rate<current_rate){
						current_rate=rate;
						best=i;
					}
				}
				
				_logger.debug(current_rate);
				_logger.debug(best);
				
				for(AID each : suppliersList) {
					_logger.debug(each.getLocalName());
				}
				
				_logger.debug("---------------------------------");
				
				if(!suppliersList.get(best).equals(_current_subscription_AID)) {
					_logger.warn("BETTER POWER SUPPLY ["+suppliersList.get(best).getLocalName()+"] FOUND");
				}
				
				return suppliersList.get(best);
				
			} else {
				_logger.error("REVIEW THISSSSSSSSSSSSSSSSSSSSSSSSSS else");
				return suppliersList.get(0);
				
			}
		}
		
	}
// ******************************* SMART METER - END ************************************************	
	
	

	
	
	

	
}
