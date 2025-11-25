#!/bin/bash
# Log streaming dashboard with tmux
# Displays logs from all services in split panes
# Requires: tmux (install with: brew install tmux or apt install tmux)

if ! command -v tmux &> /dev/null; then
    echo "Error: tmux is required but not installed."
    echo ""
    echo "Install tmux:"
    echo "  macOS:   brew install tmux"
    echo "  Ubuntu:  sudo apt install tmux"
    echo "  CentOS:  sudo yum install tmux"
    exit 1
fi

# Detect docker compose command
if docker compose version > /dev/null 2>&1; then
    DOCKER_COMPOSE="docker compose"
else
    DOCKER_COMPOSE="docker-compose"
fi

SESSION="cqrs-logs"

# Kill existing session if it exists
tmux has-session -t $SESSION 2>/dev/null && tmux kill-session -t $SESSION

# Create new session (detached)
tmux new-session -d -s $SESSION

# Split window into panes
# First split horizontally (top/bottom)
tmux split-window -v -t $SESSION:0

# Split top pane vertically (left/right)
tmux select-pane -t $SESSION:0.0
tmux split-window -h -t $SESSION:0.0

# Now we have 3 panes:
# - 0: top-left (Vault)
# - 1: top-right (PostgreSQL)
# - 2: bottom (Application - largest)

# Send commands to each pane
tmux send-keys -t $SESSION:0.0 "$DOCKER_COMPOSE logs -f vault" C-m
tmux send-keys -t $SESSION:0.1 "$DOCKER_COMPOSE logs -f postgres" C-m
tmux send-keys -t $SESSION:0.2 "$DOCKER_COMPOSE logs -f" C-m

# Set pane titles
tmux select-pane -t $SESSION:0.0 -T "Vault"
tmux select-pane -t $SESSION:0.1 -T "PostgreSQL"
tmux select-pane -t $SESSION:0.2 -T "All Services"

# Select the application pane (bottom)
tmux select-pane -t $SESSION:0.2

# Attach to session
echo "Starting log dashboard..."
echo "Press Ctrl-B then D to detach from the session"
echo "Press Ctrl-C in a pane to stop following logs"
echo ""
tmux attach-session -t $SESSION
