root = VLayout([timelineContainer])
timelineContainer = Card("card", "standard", header, [timelineComponent])
header = {title: "Deployment History"}
timelineComponent = TimeLine(timelineItems)
timelineItems = [
  {content: content1, iconType: "success"},
  {content: content2, iconType: "default"},
  {content: content3, iconType: "error"}
]
content1 = {title: "v2.1.0 deployed to production", children: [desc1]}
desc1 = Text("Production deployment completed successfully.")
content2 = {title: "v2.0.1 staged for rollout", children: [desc2]}
desc2 = Text("Rollout is pending approval.")
content3 = {title: "v2.0.0 deployment failed", children: [desc3]}
desc3 = Text("Deployment failed during verification.")