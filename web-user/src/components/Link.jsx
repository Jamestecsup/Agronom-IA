import { navigate } from "../route";

export default function Link({ to, children, ...props }) {
  return <a href={to} {...props} onClick={(event) => {
    if (event.defaultPrevented || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey || event.button !== 0) return;
    event.preventDefault();
    navigate(to);
  }}>{children}</a>;
}
