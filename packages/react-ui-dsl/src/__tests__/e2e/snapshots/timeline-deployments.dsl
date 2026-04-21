root = VLayout([timelineContainer])
timelineContainer = Card([timelineTitle, timelineComponent])
timelineTitle = Text("Deployment History", "markdown")
timelineComponent = TimeLine(timelineItems)
timelineItems = [
  {
    "content": {
      "title": "v2.1.0 deployed to production",
      "children": [
        deploymentDescription1
      ]
    },
    "iconType": "success"
  },
  {
    "content": {
      "title": "v2.0.1 staged for rollout",
      "children": [
        deploymentDescription2
      ]
    },
    "iconType": "default"
  },
  {
    "content": {
      "title": "v2.0.0 deployment failed",
      "children": [
        deploymentDescription3
      ]
    },
    "iconType": "error"
  }
]
deploymentDescription1 = Text("Production deployment completed successfully.")
deploymentDescription2 = Text("Rollout is pending approval.")
deploymentDescription3 = Text("Deployment failed during verification.")