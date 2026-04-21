root = VLayout([timelineContainer])
timelineContainer = Card([timelineHeader, timelineComponent])
timelineHeader = CardHeader("Deployment History", "Recent deployment events with status")
timelineComponent = TimeLine(timelineItems)
timelineItems = [
  timelineItem1,
  timelineItem2,
  timelineItem3
]
timelineItem1 = {content: {title: "v2.1.0 deployed to production", children: [deployDesc1]}, iconType: "success"}
deployDesc1 = Text("Production deployment completed successfully.")
timelineItem2 = {content: {title: "v2.0.1 staged for rollout", children: [deployDesc2]}, iconType: "default"}
deployDesc2 = Text("Rollout is pending approval.")
timelineItem3 = {content: {title: "v2.0.0 deployment failed", children: [deployDesc3]}, iconType: "error"}
deployDesc3 = Text("Deployment failed during verification.")